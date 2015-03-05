(ns clj-puppetdb.http
  (:require [cemerick.url :refer [url-encode map->query]]
            [cheshire.core :as json]
            [clj-puppetdb.schema :refer [Client]]
            [clj-puppetdb.util :refer [file?]]
            [clojure.edn :as edn]
            [me.raynes.fs :as fs]
            [pandect.algo.sha1 :refer [sha1]]
            [puppetlabs.http.client.sync :as http]
            [puppetlabs.kitchensink.core :refer [dissoc-in]]
            [schema.core :as s]))

;; TODO:
;;   - Validate schema for GET params. The GetParams schema
;;     exists, but needs work before it can be used.

(defn- make-https-client
  [^String host {:keys [ssl-ca-cert ssl-cert ssl-key vcr-dir]}]
  {:pre  [(every? file? [ssl-ca-cert ssl-cert ssl-key])
          (.startsWith host "https://")]
   :post [(s/validate Client %)]}
  {:host host
   :opts {:ssl-ca-cert ssl-ca-cert
          :ssl-cert    ssl-cert
          :ssl-key     ssl-key
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

(defn- vcr-filename
  [vcr-dir path]
  (str vcr-dir "/" (sha1 path) ".clj"))

(defn- vcr-transform
  [response]
  "Transform the response to avoid serialization problems"
  (dissoc-in response [:content-type :charset]))

(defn- vcr-get
  [path opts vcr-dir]
  "VCR-enabled version of GET that will check for a file containing a response first. If none is found a real GET
  request is made and the response recorded for the future."
  (if-not vcr-dir
    (http/get path opts)
    (let [file (vcr-filename vcr-dir path)]
      (when-not (fs/exists? file)
        (let [response (vcr-transform (http/get path opts))]
          (fs/mkdirs (fs/parent file))
          (spit file response)))
      ; Always read from the file - even if we just wrote it - to fast-fail on serialization errors
      ; (at the expense of performance)
      (edn/read-string (slurp file)))))

(s/defn ^:always-validate GET
  "Make a GET request using the given PuppetDB client, returning the results
  as a lazy sequence of maps with keywordized keys.

  The `path` argument must be a URL-encoded string.

  You may provide a set of querystring parameters as a map. These will be url-encoded
  automatically and added to the path."
  ([client :- Client ^String path]
    #_(println "GET:" path)                                 ;; uncomment this to watch queries
    (let [{:keys [host opts]} client
          vcr-dir (:vcr-dir opts)
          opts (dissoc opts :vcr-dir)
          response (vcr-get (str host path) (assoc opts :as :text) vcr-dir)
          body (-> response
                   :body
                   (json/decode keyword))
          headers (:headers response)]
      [body headers]))
  ([client path params]
    (if (empty? params)
      (GET client path)
      (let [query-params (map->query params)
            new-path (str path "?" query-params)]
        (GET client new-path)))))
