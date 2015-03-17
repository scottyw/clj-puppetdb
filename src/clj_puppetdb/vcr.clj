(ns clj-puppetdb.vcr
  (:require [clojure.edn :as edn]
            [clj-puppetdb.http-util :as util]
            [me.raynes.fs :as fs]
            [puppetlabs.kitchensink.core :refer [utf8-string->sha1]]
            [puppetlabs.http.client.sync :as http])
  (:import [java.io ByteArrayInputStream FileInputStream InputStreamReader BufferedReader PushbackReader
                    FileOutputStream OutputStreamWriter BufferedWriter]
           [org.apache.http.entity ContentType]
           [java.nio.charset Charset]))

(def vcr-charset (Charset/forName "UTF-8"))

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
    (update-in response [:body] #(slurp (BufferedReader. (InputStreamReader. % charset))))))

(defn body->stream
  [response]
  "Turn response body into a `java.io.InputStream` subclass."
  (let [charset (util/response-charset response)]
    (update-in response [:body] #(ByteArrayInputStream. (.getBytes % charset)))))

(defn- vcr-filename
  [vcr-dir path]
  (str vcr-dir "/" (utf8-string->sha1 path) ".clj"))

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

(defn vcr-get
  "VCR-enabled version of GET that will check for a file containing a response first. If none is found
  a real GET request is made and the response recorded for the future."
  [path opts vcr-dir]
  (let [file (vcr-filename vcr-dir path)]
    (when-not (fs/exists? file)
      (let [response (vcr-serialization-transform (http/get path opts))]
        (fs/mkdirs (fs/parent file))
        (-> file
            FileOutputStream.
            (OutputStreamWriter. vcr-charset)
            BufferedWriter.
            (spit response))))
    ; Always read from the file - even if we just wrote it - to fast-fail on serialization errors
    ; (at the expense of performance)
    (-> (with-open [reader (-> file
                               FileInputStream.
                               (InputStreamReader. vcr-charset)
                               BufferedReader.
                               PushbackReader.)]
          (edn/read reader))
        vcr-unserialization-transform)))