(ns kuti.storage
  (:require [clojure.java.io :as io]
            [clojure.string]
            [kuti.support.debugging :refer :all]
            [kuti.support :refer [path-join]]
            [kuti.record]
            [kuti.record.nested :as nested]
            [kuti.search]
            [kosa.config :as config]
            [buddy.core.hash :as hash]
            [buddy.core.codecs :refer :all]
            [mount.core :as mount :refer [defstate]])
  (:import [java.io FileNotFoundException]
           [java.lang RuntimeException]))

(defn start-storage! []
  (or (:storage (mount/args))
      (:storage config/config)))

(defstate service-config
  :start (start-storage!)
  :stop nil)

(defn attached-filename [attachment]
  (format "kuti-%s-%s" (:attm/key attachment) (:attm/filename attachment)))

(defn service-dir []
  (:root service-config))

(defn service-filename [attachment]
  (let [dir (service-dir)]
    (if (.exists (io/file dir))
      (path-join dir (attached-filename attachment))
      (throw (FileNotFoundException. (str "Directory missing: " dir))))))

(defn file->bytes [file]
  (with-open [xin (io/input-stream file)
              xout (java.io.ByteArrayOutputStream.)]
    (io/copy xin xout)
    (.toByteArray xout)))

(defn calculate-key [tempfile]
  (-> (io/input-stream tempfile)
      (hash/blake2b-128)
      (bytes->hex)))

(defn calculate-md5 [tempfile]
  (-> (io/input-stream tempfile)
      (hash/md5)
      (bytes->hex)))

(defn clean-filename [s]
  (clojure.string/replace s #"\s" "_"))

(defn save-file! [tempfile attachment]
  (.renameTo tempfile (io/file (service-filename attachment))))

(defn params->attachment! [file-params]
  (let [tempfile (:tempfile file-params)
        attachment (-> {:kuti/type         :attm
                        :attm/key          (calculate-key tempfile)
                        :attm/filename     (clean-filename (:filename file-params))
                        :attm/metadata     ""
                        :attm/service-name (:service service-config)
                        ;; unfurled:
                        :attm/checksum     (calculate-md5 tempfile)
                        :attm/content-type (:content-type file-params)
                        :attm/byte-size    (.length tempfile)
                        :attm/identified   true}
                       kuti.record/timestamp)]
    (if (save-file! tempfile attachment)
      attachment
      (throw (RuntimeException. "Uploaded file failed to save to disk.")))))

(defn put-attachment! [attachment]
  (if-let [saved (kuti.record/save! attachment)]
    saved
    (throw (RuntimeException. "Attachment failed to save to database."))))

;;;;;;;;;;;;;;;;;;
;;  PUBLIC API  ;;
;;;;;;;;;;;;;;;;;;

(defn attach!
  "`attr` must be of the form `:<name>-attachment`"
  [doc attr file-params]
  (let [attachment (params->attachment! file-params)
        attachment-in-db (put-attachment! attachment)]
    (assoc doc attr attachment-in-db)))

(defn reattach!
  "`attr` must be of the form `:<name>-attachment`
   `id` must be an existing attachment"
  [doc attr id]
  (let [attachment (kuti.record/get id)]
    (assoc doc attr attachment)))

(defn collapse-all [doc]
  (nested/collapse-all doc "attachment"))

(defn file [attachment]
  (io/file (service-filename attachment)))

(defn url [attachment]
  (path-join (:path service-config)
             (attached-filename attachment)))