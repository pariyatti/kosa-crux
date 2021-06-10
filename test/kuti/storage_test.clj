(ns kuti.storage-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [medley.core :refer [dissoc-in]]
            [mount.core :as mount]
            [kuti.support.debugging :refer :all]
            [kuti.fixtures.record-fixtures :as record-fixtures]
            [kuti.fixtures.file-fixtures :as file-fixtures]
            [kuti.fixtures.storage-fixtures :as storage-fixtures]
            [kuti.support]
            [kuti.support.time :as time]
            [kuti.record]
            [kuti.controller :as c]
            [kuti.storage.core :as core]
            [kuti.storage :as sut]
            [kuti.fixtures.time-fixtures :as time-fixtures])
  (:import [java.io FileNotFoundException]))

(use-fixtures :once
  time-fixtures/freeze-clock-1995
  record-fixtures/force-destroy-db
  record-fixtures/force-migrate-db
  record-fixtures/force-start-db)

(use-fixtures :each
  file-fixtures/with-fixture-files
  storage-fixtures/set-service-config)

(def params1 {:type :leaf-artefact
              :leaf-file {:filename "bodhi-with-raindrops.jpg",
                          :content-type "image/jpeg",
                          :tempfile (io/file "test/kuti/fixtures/files/bodhi-temp.jpg")
                          :size 13468}
              :submit "Save"})

(def ws-params {:type :leaf-artefact
                :leaf-file {:filename "bodhi with\twhitespace.jpg",
                            :content-type "image/jpeg",
                            :tempfile (io/file "test/kuti/fixtures/files/bodhi-temp.jpg")
                            :size 13468}
                :submit "Save"})

(deftest attachment
  (let [attachment (sut/params->attachment! (:leaf-file params1))]
    (testing "returns an 'attachment' document"
      (is (= {:kuti/type :attm
              :attm/updated-at @time/clock
              :attm/key "a2e0d5505185beb708ac5edaf4fc4d20"
              :attm/filename "bodhi-with-raindrops.jpg"
              :attm/content-type "image/jpeg"
              :attm/metadata ""
              :attm/service-name :disk
              :attm/byte-size 13468
              :attm/checksum "ca20bbfbea75755b1059ff2cd64bd6d3"
              :attm/identified true}
             attachment)))

    (testing "attachment's service filename identifies it as a kuti.storage file"
      (is (re-matches #"tmp/storage/kuti-.*-bodhi-with-raindrops\.jpg"
                      (sut/service-filename attachment))))

    (testing "url is prefixed with path from service config"
      (is (re-matches #"/uploads/kuti-.*-bodhi-with-raindrops\.jpg"
                      (sut/url attachment))))))

(deftest hash-key
  (let [attachment (sut/params->attachment! (:leaf-file params1))]
    (testing ":attm/key is blake2b-encoded"
      (is (= "a2e0d5505185beb708ac5edaf4fc4d20" (:attm/key attachment))))))

(deftest unfurling
  (let [attachment (sut/params->attachment! (:leaf-file params1))]
    (testing "byte size is recorded"
      (is (= 13468 (:attm/byte-size attachment))))

    (testing "md5 checksum is recorded"
      (is (= "ca20bbfbea75755b1059ff2cd64bd6d3" (:attm/checksum attachment))))))

(deftest file-size
  (testing "attached file on disk has the same length as the uploaded file"
    (let [attachment (sut/params->attachment! (:leaf-file params1))
          local-file (sut/file attachment)]
      (is (= 13468 (.length local-file))))))

(deftest missing-root-directory
  (testing "throws an exception when file copy fails"
    (mount/stop #'core/service-config)
    (-> (mount/with-args {:storage {:service :disk
                                    :root    "this/directory/does/not/exist/"
                                    :path    "/uploads"}})
        (mount/only #{#'core/service-config})
        mount/start)
    (is (thrown? java.io.FileNotFoundException
                 (sut/params->attachment! (:leaf-file params1))))))

(deftest funky-characters
  (testing "replaces whitespace with underscores"
    (let [attachment (sut/params->attachment! (:leaf-file ws-params))]
      (testing "returns an 'attachment' document with underscores"
        (is (= {:kuti/type :attm
                :attm/updated-at @time/clock
                :attm/key "a2e0d5505185beb708ac5edaf4fc4d20"
                :attm/filename "bodhi_with_whitespace.jpg"
                :attm/content-type "image/jpeg"
                :attm/metadata ""
                :attm/service-name :disk
                :attm/byte-size 13468
                :attm/checksum "ca20bbfbea75755b1059ff2cd64bd6d3"
                :attm/identified true}
               attachment))))))

(deftest attach!
  (let [doc1 (c/params->doc params1 [:type :leaf-file])
        doc2 (sut/attach! doc1 :leaf-attachment (:leaf-file doc1))]

    (testing "records the attachment to disk"
      (let [local-file (sut/file (:leaf-attachment doc2))]
        (is (= 13468 (.length local-file)))))

    (testing "records the attachment in Crux"
      (let [attachment (kuti.record/get (-> doc2 :leaf-attachment :crux.db/id))]
        (is (not (nil? (:crux.db/id attachment))))
        (is (= {:kuti/type :attm
                :attm/updated-at @time/clock
                :attm/key "a2e0d5505185beb708ac5edaf4fc4d20"
                :attm/filename "bodhi-with-raindrops.jpg"
                :attm/content-type "image/jpeg"
                :attm/metadata ""
                :attm/service-name :disk
                :attm/byte-size 13468
                :attm/checksum "ca20bbfbea75755b1059ff2cd64bd6d3"
                :attm/identified true}
               (dissoc attachment :crux.db/id)))))))

(deftest collapse-and-expand
  (let [params-w-2-attm {:type :double
                         :leaf-file {:filename "bodhi-with-raindrops.jpg",
                                     :content-type "image/jpeg",
                                     :tempfile (io/file "test/kuti/fixtures/files/bodhi-temp.jpg")
                                     :size 13468}
                         :bodhi-file {:filename "bodhi with\twhitespace.jpg",
                                      :content-type "image/jpeg",
                                      :tempfile (io/file "test/kuti/fixtures/files/bodhi-temp.jpg")
                                      :size 13468}
                         :submit "Save"}
        doc (-> (c/params->doc params-w-2-attm [:type])
                (sut/attach! :double/leaf-attachment (:leaf-file params-w-2-attm))
                (sut/attach! :double/bodhi-attachment (:bodhi-file params-w-2-attm)))
        leaf-attachment-id (-> doc :double/leaf-attachment :crux.db/id)
        bodhi-attachment-id (-> doc :double/bodhi-attachment :crux.db/id)
        collapsed (sut/collapse-all doc)]

    (testing "collapses all attachments"
      (is (not (nil? (:double/leaf-attachment-id collapsed))))
      (is (not (nil? (:double/bodhi-attachment-id collapsed))))
      (is (= {:kuti/type :double
              :double/leaf-attachment-id leaf-attachment-id
              :double/bodhi-attachment-id bodhi-attachment-id}
             collapsed)))

    (testing "expands all attachments"
      (let [expanded (sut/expand-all collapsed)]
        (is (not (nil? (:double/leaf-attachment expanded))))
        (is (not (nil? (:double/bodhi-attachment expanded))))
        (is (= {:kuti/type :double
                :double/leaf-attachment
                {:attm/byte-size 13468,
                 :attm/content-type "image/jpeg",
                 :attm/filename "bodhi-with-raindrops.jpg",
                 :attm/metadata "",
                 :kuti/type :attm,
                 :attm/updated-at #time/instant "1995-08-24T00:00:00Z",
                 :attm/checksum "ca20bbfbea75755b1059ff2cd64bd6d3",
                 :attm/service-name :disk,
                 :attm/identified true,
                 :attm/key "a2e0d5505185beb708ac5edaf4fc4d20"
                 :attm/url "/uploads/kuti-a2e0d5505185beb708ac5edaf4fc4d20-bodhi-with-raindrops.jpg"}
                :double/bodhi-attachment
                {:attm/byte-size 13468,
                 :attm/content-type "image/jpeg",
                 :attm/filename "bodhi_with_whitespace.jpg",
                 :attm/metadata "",
                 :kuti/type :attm,
                 :attm/updated-at #time/instant "1995-08-24T00:00:00Z",
                 :attm/checksum "ca20bbfbea75755b1059ff2cd64bd6d3",
                 :attm/service-name :disk,
                 :attm/identified true,
                 :attm/key "a2e0d5505185beb708ac5edaf4fc4d20"
                 :attm/url "/uploads/kuti-a2e0d5505185beb708ac5edaf4fc4d20-bodhi_with_whitespace.jpg"}}
               (-> expanded
                   (dissoc-in [:double/leaf-attachment :crux.db/id])
                   (dissoc-in [:double/bodhi-attachment :crux.db/id]))))))))

;; ***************************
;; ActiveStorage Blob Columns:
;; ***************************
;;
;; t.string   :key,          null: false
;; t.string   :filename,     null: false
;; t.string   :content_type
;; t.text     :metadata
;; t.string   :service_name, null: false
;; t.bigint   :byte_size,    null: false
;; t.string   :checksum,     null: false
;; t.datetime :created_at,   null: false


;; ***************************
;; Reference Articles
;; ***************************
;;
;; https://bloggie.io/@kinopyo/7-practical-tips-for-activestorage-on-rails-5-2
;; https://bibwild.wordpress.com/2018/10/03/some-notes-on-whats-going-on-in-activestorage/

;; Service::DiskService - https://github.com/rails/rails/blob/5cfd58bbfb8425ab1931c618d98b649bab059ce6/activestorage/lib/active_storage/service/disk_service.rb
;; Why bother doing this? To avoid too many files in one directory?
;; https://github.com/rails/rails/blob/7be33750d7e4c88d493c0e4c929eb66b8c40582d/activestorage/lib/active_storage/service/disk_service.rb#L149
