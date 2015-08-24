(ns clj-puppetdb.vcr
  (:require [cheshire.core :as json]
            [clj-puppetdb.http-core :refer :all]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [me.raynes.fs :as fs]
            [puppetlabs.kitchensink.core :refer [utf8-string->sha1]])
  (:import [java.io File ByteArrayInputStream InputStreamReader BufferedReader PushbackReader InputStream]
           [org.apache.http.entity ContentType]))

(defn rebuild-content-type
  "Rebuild the `:content-type` key in the given `response` map based on the value of the content-type
  header."
  [response]
  (let [content-type-header (get-in response [:headers "content-type"])]
    (if (empty? content-type-header)
      response
      (let [content-type (ContentType/parse content-type-header)]
        (assoc response :content-type
                        {:mime-type (.getMimeType content-type)
                         :charset   (.getCharset content-type)})))))

(defn body->string
  [response]
  "Turn response body into a string."
  (let [charset (response-charset response)]
    (->> (-> ^InputStream (get response :body)
             (InputStreamReader. charset)
             BufferedReader.
             slurp)
         (assoc response :body))))

(defn body->stream
  [response]
  "Turn response body into a `java.io.InputStream` subclass."
  (let [charset (response-charset response)]
    (->> (-> ^String (get response :body)
             (.getBytes charset)
             ByteArrayInputStream.)
         (assoc response :body))))

(defn- vcr-file
  [vcr-dir query]
  (fs/file (File. (str vcr-dir "/" (utf8-string->sha1 query) ".clj"))))

(defn- vcr-serialization-transform
  "Prepare the response for searialization."
  [response]
  (-> response
      body->string
      (dissoc :content-type)))

(defn- vcr-unserialization-transform
  "Rebuild the response after unseralization."
  [response]
  (-> response
      rebuild-content-type
      body->stream))

(def nested-params
  "parameters which contain nested maps"
  ; TODO remove :order-by when we drop support for PDB API older than v4
  [:order_by :order-by])

(defn- normalize-params
  "certain parmas (notably order_by) contain nested maps and if the VCR is running we want to
  enforce a specific ordering to give us URL stability"
  [params]
  (reduce
    (fn [params key]
      (if (contains? params key)
        (let [value (get params key)]
          (->>
            ; if the value is a string then we assume it is JSON encoded in which case we
            ; need to decode it first
            (if (string? value)
              (json/decode value)
              value)
            (map #(into (sorted-map) %))
            (assoc params key)))
        params))
    params
    nested-params))

(defn make-vcr-client
  "Make VCR-enabled version of the supplied `client` that will check for a file containing
  a response first. If none is found the original client is called to obtain the response,
  which is then recorded for the future."
  [vcr-dir client]
  (reify
    PdbClient
    (pdb-get [this path params]
      (pdb-get this this path params))
    (pdb-get [_ that path params]
      ; Sort the known nested structures in the query parameters to give us URL stability and
      ; then delegate to the original client.
      (->> params
           normalize-params
           (pdb-get client that path)))

    (pdb-do-get [_ query]
      (let [file (vcr-file vcr-dir query)]
        (when-not (fs/exists? file)
          (let [response (->> query
                              (pdb-do-get client)
                              vcr-serialization-transform)]
            (fs/mkdirs (fs/parent file))
            (-> file
                io/writer
                (spit response))))
        ; Always read from the file - even if we just wrote it - to fast-fail on serialization errors
        ; (at the expense of performance)
        (-> (with-open [reader (-> file
                                   io/reader
                                   PushbackReader.)]
              (edn/read reader))
            vcr-unserialization-transform)))

    (client-info [_]
      (-> client
          client-info
          (assoc :vcr-dir vcr-dir)))))
