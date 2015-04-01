(ns clj-puppetdb.util
  (:import [java.io File]))

(defn file?
  [^String f]
  (if (nil? f)
    false
    (-> f File. .isFile)))
