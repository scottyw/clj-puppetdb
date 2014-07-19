(ns clj-puppetdb.query
  (:require [clojure.walk :refer [postwalk]]
            [cheshire.core :as json]))

(def ops
  {'= "="
   '> ">"
   '< "<"
   '>= ">="
   '<= "<="
   'match "~"
   'null? "null?"
   'nil? "null?"
   'or "or"
   'and "and"})

(defn operator?
  [x]
  (contains? ops x))

(defn query-walk
  "This does most of the hard work for generating a query.
  It's intended to be applied recursively to each and every
  element of an expression to ensure that the final result
  can be converted straightforwardly into JSON. Just about
  anything that can be turned into a string will be, but
  booleans and numbers pass through unchanged."
  [x]
  (cond
   (operator? x) (ops x)
   (list? x) (vec x)
   (instance? java.util.regex.Pattern x) (str x)
   (instance? clojure.lang.Named x) (name x)
   (string? x) x
   :else x))

(defn query'
  "The function behind the query macro. Takes a quoted
  S-expression and turns it into a JSON array. Call this
  directly if you can't use the macro."
  [q]
  (json/encode (postwalk query-walk q)))

(defmacro query
  "Takes an unquoted S-expression representing a PuppetDB
  query and returns a JSON array suitable for sending to
  PuppetDB."
  [q]
  `(json/encode ~(postwalk query-walk q)))

(defmacro qv
  "EXPERIMENTAL: return a Query Vector that can be turned into
  a JSON query later. This might let the expression include vars."
  [q]
  `(postwalk query-walk ~q))
