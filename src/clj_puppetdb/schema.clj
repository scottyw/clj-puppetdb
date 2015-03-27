(ns clj-puppetdb.schema
  (:require [schema.core :refer [Any Str Int Keyword] :as s]))

(def Client
  "Schema for PuppetDB client maps. Note: doesn't check that
  the certs (if provided) actually exist."
  {:host Str
   :opts (s/either {:ssl-context javax.net.ssl.SSLContext
                    :vcr-dir     (s/maybe Str)}
                   {:vcr-dir (s/maybe Str)})})

(def PagingParams
  "Schema for params passed to lazy-query."
  {(s/required-key :limit) Int
   (s/optional-key :offset) Int
   (s/required-key :order-by) [{:field (s/either Keyword Str) :order Str}]
   (s/optional-key :query) clojure.lang.PersistentVector})

(def GetParams
  "Params ready to be passed to GET. Similar to PagingParams, except
  that everything is optional and :query (if present) must be a string."
  {(s/optional-key :limit) Int
   (s/optional-key :offset) Int
   (s/optional-key :order-by) [{:field (s/either Keyword Str) :order Str}]
   (s/optional-key :query) Str})
