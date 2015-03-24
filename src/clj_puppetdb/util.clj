(ns clj-puppetdb.util
  (:import [java.io File]
           [java.net URL]))

(defn file?
  [^URL f]
  (-> f .toURI File. .isFile))