(ns clj-puppetdb.util)

(defn file?
  [^java.io.File f]
  (.isFile f))
