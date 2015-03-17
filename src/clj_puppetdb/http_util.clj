(ns clj-puppetdb.http-util
  (:import [java.nio.charset Charset]
           [java.io BufferedReader InputStreamReader]))

(def default-charset (Charset/forName "UTF-8"))

(defn response-charset
  "Get the charset to use for decoding of the response body."
  [response]
  (if-let [charset (get-in response [:content-type :charset])]
    charset
    default-charset))

(defn make-response-reader
  "Create a buffered reader for reading the response body."
  [response]
  (BufferedReader. (InputStreamReader. (:body response) (response-charset response))))