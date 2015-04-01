(ns clj-puppetdb.schema
  (:require [schema.core :refer [Any Str Int Keyword] :as s])
  (:import [clojure.lang IFn]))

(def Client
  "Schema for PuppetDB client map. Note: doesn't check the
  arity of the function."
  IFn)

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
