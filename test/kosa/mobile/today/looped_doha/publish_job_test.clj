(ns ^:database kosa.mobile.today.looped-doha.publish-job-test
  (:require [clojure.test :refer :all]
            [kuti.fixtures.record-fixtures :as record-fixtures]
            [kuti.fixtures.time-fixtures :as time-fixtures]
            [kosa.fixtures.model-fixtures :as model]
            [kosa.mobile.today.looped-doha.publish-job :as sut]
            [kosa.mobile.today.looped-doha.db :as loop-db]
            [kosa.mobile.today.doha.db :as doha-db]
            [kuti.support.time :as time]))

(use-fixtures :each
  time-fixtures/freeze-clock-1995
  record-fixtures/force-destroy-db
  record-fixtures/force-migrate-db
  record-fixtures/force-start-db)

(deftest publishing
  (testing "publishes a new doha card from looped template"
    (loop-db/save! (model/looped-doha
                    {:looped-doha/doha "tara"
                     :looped-doha/translations [["en" "star"]]}))
    (sut/run-job! nil)
    (let [tara (doha-db/find-all :doha/doha "tara")]
      (is (= 1 (count tara))))))

(deftest ignores-an-empty-collection-of-looped-cards
  (testing "nothing happens (including no errors)"
    (is (nil? (sut/run-job! nil)))))

(deftest does-not-publish-more-than-once-per-day
  (testing "ignores a looped card it has already published"
    (loop-db/save! (model/looped-doha
                    {:looped-doha/doha "abaddha"
                     :looped-doha/translations [["en" "unfettered"]]}))
    (sut/run-job! nil)
    (sut/run-job! nil)
    (let [card (doha-db/find-all :doha/doha "abaddha")]
      (is (= 1 (count card))))))

(deftest looping
  (testing "does not ignore looped cards published on other days"
    (time/freeze-clock! (time/parse "2005-05-01"))
    (loop-db/save! (model/looped-doha
                    {:looped-doha/doha "abhaya"
                     :looped-doha/translations [["en" "fearless"]]}))
    (sut/run-job! nil)
    (time/freeze-clock! (time/parse "2005-06-02"))
    (sut/run-job! nil)
    (let [cards (doha-db/find-all :doha/doha "abhaya")]
      (is (= 2 (count cards)))
      (is (= #{(time/parse "2005-05-01") (time/parse "2005-06-02")}
             (set (map :doha/published-at cards)))))))

(deftest scheduling-against-epoch
  (testing "publishes the Nth card from the 'perl epoch' on 2005-04-29"
    (loop-db/save! (model/looped-doha
                    {:looped-doha/doha "canda"
                     :looped-doha/translations [["en" "moon"]]}))
    (loop-db/save! (model/looped-doha
                    {:looped-doha/doha "suriya"
                     :looped-doha/translations [["en" "sun"]]}))
    (loop-db/save! (model/looped-doha
                    {:looped-doha/doha "kujagaha"
                     :looped-doha/translations [["en" "mars"]]}))
    (loop-db/save! (model/looped-doha
                    {:looped-doha/doha "medini"
                     :looped-doha/translations [["en" "earth"]]}))
    (time/freeze-clock! (time/parse "2005-04-30"))
    (sut/run-job! nil)
    (let [all (doha-db/list)]
      (is (= 1 (count all)))
      (is (= "suriya" (:doha/doha (first all)))))))
