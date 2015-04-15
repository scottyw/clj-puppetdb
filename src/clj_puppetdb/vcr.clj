(ns clj-puppetdb.vcr
  (:require [cheshire.core :as json]
            [clj-puppetdb.http-util :as util]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [me.raynes.fs :as fs]
            [puppetlabs.kitchensink.core :refer [utf8-string->sha1]])
  (:import [java.io File ByteArrayInputStream InputStreamReader BufferedReader PushbackReader]
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
  (let [charset (util/response-charset response)]
    (->> (-> (get response :body)
             (InputStreamReader. charset)
             BufferedReader.
             slurp)
         (assoc response :body))))

(defn body->stream
  [response]
  "Turn response body into a `java.io.InputStream` subclass."
  (let [charset (util/response-charset response)]
    (->> (-> (get response :body)
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
  (fn
    ; This arity-overloaded variant of the function sorts the known nested structures in the
    ; query parameters to give us URL stability and then delegates to the original client.
    ; Note that under normal circumstances the `this` parameter points to this very function
    ; but when it is passed to the original client, it effectively overrides the other
    ; arity-overloaded variants of the function for the client - i.e. it makes the client
    ; call our versions of the arity-overloaded variants of the function.
    ([this path params]
     (->> params
          normalize-params
          (client this path)))

    ; This arity-overloaded variant of the function is called from the original client (thanks
    ; to the override effect described above) to actually execute the PDB query.
    ; It first tries to find a response for the query recorded in a file and only delegates to
    ; the original client if no recorded response for the query is found.
    ([query]
     (let [file (vcr-file vcr-dir query)]
       (when-not (fs/exists? file)
         (let [response (-> query
                            client
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

    ; This arity-overloaded variant of the function just adds the vcr-dir info to the info map
    ; returned by the original client.
    ([]
     (-> (client)
         (assoc :vcr-dir vcr-dir)))))
