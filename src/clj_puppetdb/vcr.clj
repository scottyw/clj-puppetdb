(ns clj-puppetdb.vcr
  (:require [clojure.edn :as edn]
            [me.raynes.fs :as fs]
            [puppetlabs.kitchensink.core :refer [utf8-string->sha1]]
            [puppetlabs.http.client.sync :as http]
            [puppetlabs.kitchensink.core :refer [dissoc-in]]))

(defn- vcr-filename
  [vcr-dir path]
  (str vcr-dir "/" (utf8-string->sha1 path) ".clj"))

(defn- vcr-transform
  [response]
  "Transform the response to avoid serialization problems"
  (dissoc-in response [:content-type :charset]))

(defn vcr-get
  "VCR-enabled version of GET that will check for a file containing a response first. If none is found a real GET
  request is made and the response recorded for the future."
  [path opts vcr-dir]
  (let [file (vcr-filename vcr-dir path)]
    (when-not (fs/exists? file)
      (let [response (vcr-transform (http/get path opts))]
        (fs/mkdirs (fs/parent file))
        (spit file response)))
    ; Always read from the file - even if we just wrote it - to fast-fail on serialization errors
    ; (at the expense of performance)
    (edn/read-string (slurp file))))
