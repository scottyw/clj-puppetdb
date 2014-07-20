(ns clj-puppetdb.query-test
  (:require [clojure.test :refer :all]
            [clj-puppetdb.query :refer [query->json]]))

(deftest query->json-test
  (testing "The dreaded ~ operator"
    (is (= (query->json [:match :certname #"web\d+"])
           "[\"~\",\"certname\",\"web\\\\d+\"]")))
  (testing "Nested expressions"
    (is (= (query->json [:>= [:fact "uptime_days"] 10])
           "[\">=\",[\"fact\",\"uptime_days\"],10]")))
  (testing "Booleans don't get converted to strings"
    (is (= (query->json [:= [:fact "is_virtual"] false])
           "[\"=\",[\"fact\",\"is_virtual\"],false]"))))
