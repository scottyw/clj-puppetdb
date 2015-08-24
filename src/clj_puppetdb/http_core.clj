(ns clj-puppetdb.http-core
  (:import [java.nio.charset Charset]
           [java.io BufferedReader InputStreamReader InputStream]))

(def default-charset (Charset/forName "UTF-8"))

(defprotocol PdbClient
  "PDB API low level HTTP client protocol."
  (pdb-get [this path params] [this that path params]
    "Build the query URL and submit the PDB query.")

  (pdb-do-get [this query]
    "Do submit the PDB query.")

  (client-info [this]
    "Get PDB client info map."))

(defn response-charset
  "Get the charset to use for decoding of the response body."
  ^Charset [response]
  (if-let [charset (get-in response [:content-type :charset])]
    charset
    default-charset))

(defn make-response-reader
  "Create a buffered reader for reading the response body."
  [response]
  (BufferedReader. (InputStreamReader. ^InputStream (:body response) (response-charset response))))
