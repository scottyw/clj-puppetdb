(ns clj-puppetdb.http-core-test
  (:require [clojure.test :refer :all]
            [clj-puppetdb.http-core :as http-core])
  (:import (java.nio.charset Charset)))

(deftest response-charset-test
  (testing "Response contains charset"
    (let [charset (Charset/forName "US-ASCII")]
      (is (= (http-core/response-charset {:body "Lorem ipsum" :content-type {:charset charset}})
             charset))))
  (testing "Response doesn't contain charset"
    (is (= (http-core/response-charset {:body "Lorem ipsum"})
           http-core/default-charset))))
