(ns ^:database kosa.mobile.today.pali-word.rss-job-db-test
  (:require [kosa.mobile.today.pali-word.rss-job :as sut]
            [kosa.mobile.today.pali-word.db :as db]
            [clojure.test :refer :all]
            [kuti.fixtures.record-fixtures :as record-fixtures]
            [kuti.fixtures.time-fixtures :as time-fixtures])
  (:import [java.net URI]))

(use-fixtures :once
  time-fixtures/freeze-clock-1995
  record-fixtures/force-destroy-db
  record-fixtures/force-migrate-db
  record-fixtures/force-start-db)

(deftest ^:integration polling
  (testing "only gets feed updates once with If-None-Match and If-Modified-Since HTTP headers"
    (let [first-feed (sut/poll)
          second-feed (sut/poll)]
      (is (not (nil? first-feed)))
      (is (nil? second-feed)))))

(deftest parsing
  (testing "alerts when feed nodes can't be parsed"
    (is (thrown? java.lang.ClassCastException
                 (sut/parse {:entries '({:description {:value {:a-string "is expected here"}}
                                         :uri "https://ignored"
                                         :published-date #inst "2021-02-09T16:11:01.000-00:00"})}))))

  (testing "alerts when feed nodes are mis-ordered"
    (is (thrown? clojure.lang.ExceptionInfo
                 (sut/parse {:entries '({:description {:value-not-found "<html>wrong node</html>"}
                                         :uri "https://ignored"
                                         :published-date #inst "2021-02-09T16:11:01.000-00:00"})})))

    (is (thrown? clojure.lang.ExceptionInfo
                 (sut/parse {:entries '({:description {:value "some <br /> html"}
                                         :uri-not-found "https://ignored"
                                         :published-date #inst "2021-02-09T16:11:01.000-00:00"})})))))

(deftest database
  (testing "does not insert the same entity twice"
    (let [feed {:entries '({:description {:value "anta — end, goal, limit"}
                            :uri "https://ignored"
                            :published-date #inst "2021-02-09T16:11:01.000-00:00"})}]
      (sut/parse feed)
      (sut/parse feed)
      (is (= 1 (count (db/list)))))))
