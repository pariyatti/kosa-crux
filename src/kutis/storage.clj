(ns kutis.storage
  (:require [clojure.java.io :as io]
            [kutis.support :refer [path-join]]
            [kutis.record]
            [kutis.record.nested :as nested]
            [kutis.search])
  (:import [com.twmacinta.util MD5]))

(def attachment-fields #{:key :filename :content-type :metadata :service-name :byte-size :checksum})

(def service-config (atom {}))

(defn set-service-config! [conf]
  (reset! service-config conf))

(defn attached-filename [attachment]
  (format "kutis-%s-%s" (:key attachment) (:filename attachment)))

(defn service-filename [attachment]
  (path-join (:root @service-config)
             (attached-filename attachment)))

(defn file->bytes [file]
  (with-open [xin (io/input-stream file)
              xout (java.io.ByteArrayOutputStream.)]
    (io/copy xin xout)
    (.toByteArray xout)))

(defn calculate-key [tempfile]
  (let [bytes (file->bytes tempfile)
        ;; TODO: replace with blake2b
        h (hash bytes)]
    h))

(defn save-file! [tempfile attachment]
  (.renameTo tempfile (io/file (service-filename attachment))))

(defn params->attachment! [file-params]
  (let [tempfile (:tempfile file-params)
        k (calculate-key tempfile)
        attachment {:key k
                    :filename (:filename file-params)
                    :content-type (:content-type file-params)
                    :metadata     ""
                    :service-name (:service @service-config)
                    :byte-size    (.length tempfile)
                    :checksum     (-> tempfile (MD5/getHash) (MD5/asHex))
                    }
        _ (save-file! tempfile attachment)]
    attachment))

(defn put-attachment! [attachment]
  (if-let [saved (kutis.record/put attachment attachment-fields)]
    saved ;; (:crux.db/id attachment)
    (throw (ex-info "Attachment not saved."))))

;;;;;;;;;;;;;;;;;;
;;  PUBLIC API  ;;
;;;;;;;;;;;;;;;;;;

(defn attach!
  "`attr` must be of the form `:<name>-attachment`"
  [doc attr file-params]
  (let [attachment (params->attachment! file-params)
        attachment-in-db (put-attachment! attachment)]
    (assoc doc attr attachment-in-db)))

(defn collapse-all [doc]
  (nested/collapse-all doc "attachment"))

(defn file [attachment]
  (io/file (service-filename attachment)))

(defn url [attachment]
  (path-join (:path @service-config)
             (attached-filename attachment)))
