(ns clj-puppetdb.connect
  (:require [clojure.java.io :as io]
            [puppetlabs.http.client.sync :as http]
            [cheshire.core :as json]
            [schema.core :as s])
  (:refer-clojure :exclude [get]))

(defn file?
  [^java.io.File f]
  (.isFile f))

(def Connection
  "Schema for PuppetDB connections. Note: doesn't check that
  the certs (if provided) actually exist."
  {:host s/Str
   :opts (s/either {:ssl-ca-cert java.io.File
                    :ssl-cert java.io.File
                    :ssl-key java.io.File}
                   {})})

(defn- make-https-connection
  [^String host {:keys [ssl-ca-cert ssl-cert ssl-key]}]
  {:pre [(every? file? [ssl-ca-cert ssl-cert ssl-key])
         (.startsWith host "https://")]
   :post [(s/validate Connection %)]}
  {:host host
   :opts {:ssl-ca-cert ssl-ca-cert
          :ssl-cert ssl-cert
          :ssl-key ssl-key}})

(defn- make-http-connection
  [^String host]
  {:pre [(.startsWith host "http://")]
   :post [(s/validate Connection %)]}
  {:host host
   :opts {}})

(defn- connection?
  "Validates that the supplied map is a connection."
  [connection]
  (and
    (contains? connection :host)
    (contains? connection :opts)))

(defn connect
  "Return a PuppetDB connection map for the given host.

  If the host begins with 'https://', you must supply also supply a map containing
  a valid :ssl-ca-cert, :ssl-cert, and :ssl-key (as java.io.File objects).
 
  If the host begins with 'http://', that must be the only argument given.

  Either way, you must specify the port as part of the hostname (usually ':8080' for
  http or ':8081' for https)."
  ([^String host] (make-http-connection host))
  ([^String host opts] (make-https-connection host opts)))

(s/defn ^:always-validate get
  "Make a GET request using the given PuppetDB connection, returning the results
  as a lazy sequence of maps with keyword keys. Doesn't support paging (yet).

  The `path` argument should be a URL-encoded string."
  [connection :- Connection ^String path]
  (let [{:keys [host opts]} connection]
    (-> (http/get (str host path) opts)
        :body
        (json/decode keyword))))
