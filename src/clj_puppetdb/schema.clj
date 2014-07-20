(ns clj-puppetdb.schema
  (:require [schema.core :refer [Str either]]))

(def Connection
  "Schema for PuppetDB connections. Note: doesn't check that
  the certs (if provided) actually exist."
  {:host Str
   :opts (either {:ssl-ca-cert java.io.File
                    :ssl-cert java.io.File
                    :ssl-key java.io.File}
                   {})})
