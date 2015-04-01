(ns clj-puppetdb.http
  (:require [cemerick.url :refer [url-encode map->query]]
            [cheshire.core :as json]
            [clj-puppetdb.http-util :as util]
            [clj-puppetdb.schema :refer [Client]]
            [clj-puppetdb.util :refer [file?]]
            [clj-puppetdb.vcr :refer [vcr-get]]
            [puppetlabs.ssl-utils.core :as ssl]
            [puppetlabs.http.client.sync :as http]
            [schema.core :as s])
  (:import [java.io IOException File]
           [javax.net.ssl SSLContext]
           [com.fasterxml.jackson.core JsonParseException]))

;; TODO:
;;   - Validate schema for GET params. The GetParams schema
;;     exists, but needs work before it can be used.


(def cert-keys
  "The keys to the configuration map specifying the certificates/private key
  needed for creating the SSL context.
  Warning: the order of the keys must match that expected by the
  `puppetlabs.ssl-utils.core/pems->ssl-context` function."
  [:ssl-cert :ssl-key :ssl-ca-cert])

(defn- make-https-client
  [^String host {:keys [ssl-context vcr-dir] :as opts}]
  {:pre  [(.startsWith host "https://")
          (or (and ssl-context (or (instance? SSLContext ssl-context)
                                   (throw (IllegalArgumentException.
                                            (str "The ssl-context is expected to be an instance of "
                                                 (-> SSLContext .getName)
                                                 " but is of class "
                                                 (-> ssl-context .getClass .getName))))))
              (every? #(or (file? (get opts %))
                           (throw (IllegalArgumentException.
                                    (str "The following file does not exist: " (name %) \= (get opts %)))))
                      cert-keys))]
   :post [(s/validate Client %)]}
  {:host host
   :opts {:ssl-context (if ssl-context
                         ssl-context
                         (apply ssl/pems->ssl-context (map #(->> % (get opts) File.) cert-keys)))
          :vcr-dir     vcr-dir}})

(defn- make-http-client
  [^String host {:keys [vcr-dir]}]
  {:pre  [(.startsWith host "http://")]
   :post [(s/validate Client %)]}
  {:host host
   :opts {:vcr-dir vcr-dir}})

(defn make-client
  [^String host opts]
  {:pre [host]}
  (cond
    (.startsWith host "http://") (make-http-client host opts)
    (.startsWith host "https://") (make-https-client host opts)
    :else (throw (IllegalArgumentException. "Host must start either http:// or https://"))))

(defn- http-get
  "Decide whether to use the VCR to service the get request"
  [path opts vcr-dir]
  (if vcr-dir
    (vcr-get path opts vcr-dir)
    (http/get path opts)))

(defmacro catching-exceptions
  "Execute the `call` in a try-catch block, catching the named `exceptions` (or any subclasses
  of `java.lang.Throwable`) and rethrowing them as :exception in the `exception-structure`."
  [call exception-structure & exceptions]
  (let [exceptions (if (empty? exceptions) [Throwable] exceptions)]
    `(try
       ~call
       ~@(map (fn [exception]
                `(catch ~exception exception#
                   (throw (ex-info nil (assoc ~exception-structure :exception exception#)))))
              exceptions))))

(defmacro catching-parse-exceptions
  "A convenience macro for wrapping JSON parsing code. It simply delegates to the
  `catching-exceptions` macro supplying arguments to it suitable for the JSON parsing."
  [call]
  `(catching-exceptions ~call {:kind :puppetdb-parse-error} JsonParseException IOException))

(defn- lazy-seq-catching-parse-exceptions
  "Given a lazy sequence wrap it into another lazy sequence which ensures that proper error
  handling is in place whenever an element is consumed from the sequence."
  [result]
  (lazy-seq
    (if-let [sequence (catching-parse-exceptions (seq result))]
      (cons (first sequence) (lazy-seq-catching-parse-exceptions (rest sequence)))
      result)))

(defn- decode-stream-catching-parse-exceptions
  "JSON decode data from given reader making sure proper error handling is in place."
  [reader]
  (let [result (catching-parse-exceptions (json/decode-stream reader keyword))]
    (if (seq? result)
      (lazy-seq-catching-parse-exceptions result)
      result)))

(s/defn ^:always-validate GET
  "Make a GET request using the given PuppetDB client, returning the results
  as a clojure data structure. If the structure contains any maps then keys
  in those maps will be keywordized.

  The `path` argument must be a URL-encoded string.

  You may provide a set of querystring parameters as a map. These will be url-encoded
  automatically and added to the path."
  ([client :- Client ^String path params]
    {:pre (map? params)}
    (let [query (if (empty? params)
                  path
                  (str path "?" (map->query params)))]
      #_(println "GET:" query)                                 ;; uncomment this to watch queries
      (let [{:keys [host opts]} client
            vcr-dir (:vcr-dir opts)
            opts (dissoc opts :vcr-dir)
            response (-> (str host query)
                         (http-get (assoc opts :as :stream) vcr-dir)
                         (catching-exceptions {:kind :puppetdb-connection-error}))]
        (if-not (= 200 (:status response))
          (throw (ex-info nil {:kind   :puppetdb-query-error
                               :url    (str host path)
                               :msg    (slurp (util/make-response-reader response))
                               :status (:status response)
                               :params params})))
        (let [data (-> response
                       util/make-response-reader
                       decode-stream-catching-parse-exceptions)
              headers (:headers response)]
          [data headers]))))
  ([client path]
    (GET client path {})))