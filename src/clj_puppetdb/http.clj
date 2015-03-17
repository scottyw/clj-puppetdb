(ns clj-puppetdb.http
  (:require [cemerick.url :refer [url-encode map->query]]
            [cheshire.core :as json]
            [clj-puppetdb.http-util :as util]
            [clj-puppetdb.schema :refer [Client]]
            [clj-puppetdb.util :refer [file?]]
            [clj-puppetdb.vcr :refer [vcr-get]]
            [puppetlabs.http.client.sync :as http]
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

(defn- http-get
  "Decide whether to use the VCR to service the get request"
  [path opts vcr-dir]
  (if vcr-dir
    (vcr-get path opts vcr-dir)
    (http/get path opts)))

(s/defn ^:always-validate GET
  "Make a GET request using the given PuppetDB client, returning the results
  as a clojure data structure. If the structure contains any maps then keys
  in those maps will be keywordized.

  The `path` argument must be a URL-encoded string.

  You may provide a set of querystring parameters as a map. These will be url-encoded
  automatically and added to the path."
  ([client :- Client ^String path params]
    {:pre (map? params)}
    (let [path (if (empty? params)
                 path
                 (str path "?" (map->query params)))]
      #_(println "GET:" path)                                 ;; uncomment this to watch queries
      (let [{:keys [host opts]} client
            vcr-dir (:vcr-dir opts)
            opts (dissoc opts :vcr-dir)
            response (http-get (str host path) (assoc opts :as :stream) vcr-dir)]
        (if-not (= 200 (:status response))
          (throw (RuntimeException. (slurp (util/make-response-reader response)))))
        (let [data (-> response
                       util/make-response-reader
                       (json/decode-stream keyword))
              headers (:headers response)]
          [data headers]))))
  ([client path]
    (GET client path {})))