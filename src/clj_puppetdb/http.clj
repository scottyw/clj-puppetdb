(ns clj-puppetdb.http
  (:require [cemerick.url :refer [map->query]]
            [cheshire.core :as json]
            [clj-puppetdb.http-util :as util]
            [clj-puppetdb.query :as q]
            [clj-puppetdb.schema :refer [Client]]
            [clj-puppetdb.vcr :refer [make-vcr-client]]
            [clojure.tools.logging :as log]
            [me.raynes.fs :as fs]
            [puppetlabs.http.client.sync :as http]
            [puppetlabs.ssl-utils.core :as ssl]
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

(def connection-relevant-opts
  [:ssl-context :connect-timeout-milliseconds :socket-timeout-milliseconds])

(defn- make-client-common
  [^String host opts]
  (let [opts (assoc opts :as :stream)
        info (select-keys opts connection-relevant-opts)
        info (assoc info :host host)]
    (fn
      ; This arity-overloaded variant of the function is the one called by the `GET` function.
      ; The `this` parameter refers under normal circumstances to this very function and it is
      ; needed to be able to call another arity-overloaded variant of the function from the
      ; function body. When the `this` parameter refers to a different function then it
      ; effectively overrides the other arity-overloaded variants of the function.
      ([this path params]
       (let [query (if (empty? params)
                     (str host path)
                     (str host path \? (-> params
                                           q/params->json
                                           map->query)))]
         ; now call the single argument variant of the function to execute the query
         (this query)))

      ; This arity-overloaded variant of the function is the one which actually executes
      ; the PDB query.
      ([query]
       (log/debug (str "GET:" query))
       (http/get query opts))

      ; This arity-overloaded variant of the function returns a map with information about
      ; this client.
      ([]
       info))))

(defn- file?
  [^String file-path]
  (if (nil? file-path)
    nil
    (-> file-path
        File.
        fs/file?)))

(defn- make-https-client
  [^String host {:keys [ssl-context] :as opts}]
  {:pre [(.startsWith host "https://")
         (map? opts)
         (or (and ssl-context (or (instance? SSLContext ssl-context)
                                  (throw (IllegalArgumentException.
                                           (str "The ssl-context is expected to be an instance of "
                                                (-> SSLContext .getName)
                                                " but is of class "
                                                (-> ssl-context .getClass .getName))))))
             (every? #(or (->> % (get opts) file?)
                          (throw (IllegalArgumentException.
                                   (str "The following file does not exist: " (name %) \= (get opts %)))))
                     cert-keys))]}
  (let [opts (if ssl-context
               opts
               (assoc opts :ssl-context (apply ssl/pems->ssl-context (map #(->> % (get opts) File. fs/file) cert-keys))))
        opts (apply dissoc opts cert-keys)]
    (make-client-common host opts)))

(defn- make-http-client
  [^String host opts]
  {:pre [(.startsWith host "http://")
         (map? opts)]}
  (let [opts (apply dissoc opts :ssl-context cert-keys)]
    (make-client-common host opts)))

(defn make-client
  [^String host opts]
  {:post [(s/validate Client %)]}
  (let [vcr-dir (:vcr-dir opts)
        opts (dissoc opts :vcr-dir)
        client (cond
                 (.startsWith host "http://") (make-http-client host opts)
                 (.startsWith host "https://") (make-https-client host opts)
                 :else (throw (IllegalArgumentException. "Host must start either http:// or https://")))]
    (if vcr-dir
      (make-vcr-client vcr-dir client)
      client)))

(defmacro assoc-kind
  "Associate the supplied `kind` value with the :kind key in the given `exception-structure` map."
  [exception-structure kind]
  `(assoc ~exception-structure :kind ~kind))

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
  [call exception-structure]
  `(catching-exceptions
     ~call
     (assoc-kind ~exception-structure :puppetdb-parse-error) JsonParseException IOException))

(defn- lazy-seq-catching-parse-exceptions
  "Given a lazy sequence wrap it into another lazy sequence which ensures that proper error
  handling is in place whenever an element is consumed from the sequence."
  [result exception-structure]
  (lazy-seq
    (if-let [sequence (catching-parse-exceptions (seq result) exception-structure)]
      (cons (first sequence) (lazy-seq-catching-parse-exceptions (rest sequence) exception-structure))
      result)))

(defn- decode-stream-catching-parse-exceptions
  "JSON decode data from given reader making sure proper error handling is in place."
  [reader exception-structure]
  (let [result (catching-parse-exceptions (json/decode-stream reader keyword) exception-structure)]
    (if (seq? result)
      (lazy-seq-catching-parse-exceptions result exception-structure)
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
    (let [query-info (-> (client)                           ; get client info
                         (assoc :endpoint path)
                         (assoc :params   params))
          response (-> (client client path params)
                       (catching-exceptions (assoc-kind query-info :puppetdb-connection-error)))]
      (if-not (= 200 (:status response))
        (throw (ex-info nil (-> query-info
                                (assoc-kind :puppetdb-query-error)
                                (assoc :status (:status response))
                                (assoc :msg    (slurp (util/make-response-reader response)))))))
      (let [data (-> response
                     util/make-response-reader
                     (decode-stream-catching-parse-exceptions query-info))
            headers (:headers response)]
        [data headers])))

  ([client path]
    (GET client path {})))
