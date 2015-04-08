(ns clj-puppetdb.http-util-test
  (:require [clojure.test :refer :all]
            [clj-puppetdb.http-util :as util])
  (:import (java.nio.charset Charset)))

(deftest response-charset-test
  (testing "Response contains charset"
    (let [charset (Charset/forName "US-ASCII")]
      (is (= (util/response-charset {:body "Lorem ipsum" :content-type {:charset charset}})
             charset))))
  (testing "Response doesn't contain charset"
    (is (= (util/response-charset {:body "Lorem ipsum"})
           util/default-charset))))
