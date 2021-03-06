(ns kosa.mobile.today.looped-doha.txt
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [kosa.mobile.today.looped.txt :as txt]
            [kosa.mobile.today.looped-doha.db :as db]
            [kuti.support.types :as types]
            [kuti.support.strings :as strings]
            [kuti.storage :as storage]
            [kuti.storage.open-uri :as open-uri])
  (:import [java.net URI]))

(defn shred [marker entry]
  (->> (str/split entry (re-pattern marker))
       (map strings/trim!)
       vec))

(defn repair [marker pair]
  [(first pair) (str marker (second pair))])

(defn ->audio-url [url]
  (-> (str/split url #": ")
      second
      strings/trim!
      (URI.)))

(defn shred-blocks [lang v]
  (let [all-blocks (str/split (second v) (re-pattern "\n\\s*\n"))
        audio-url (first all-blocks)
        translation (->> all-blocks
                         (drop 1)
                         (str/join "\n\n"))]
    #:looped-doha
    {:doha (first v)
     :audio-url (->audio-url audio-url)
     :translations [[lang translation]]}))

(deftype DohaIngester []
  txt/Ingester

  (attr-for [_ kw]
    (types/namespace-kw :looped-doha kw))

  (entry-attr [_]
    :looped-doha/doha)

  (human-name [_]
    "Daily Doha")

  (parse [_ txt lang]
    (let [marker (get {"en" "Listen"
                       "lt" "Klausytis"
                       "pt" "Escute o áudio"
                       "zh" "聆聽"} lang)
          m (str marker ": ")]
      (->> (txt/split-file txt)
           (map strings/trim!)
           (map #(shred m %))
           (map #(repair m %))
           (map #(shred-blocks lang %)))))

  (find-existing [_ doha]
    (first (db/find-all :looped-doha/doha (:looped-doha/doha doha))))

  (db-insert* [_ doha]
    (db/save! (merge #:looped-doha
                     {:original-doha (:looped-doha/doha doha)
                      :original-url (URI. "")}
                     doha)))

  (citations [_ _entity]
    {})

  (download-attachments! [_ lang e]
    (if (= "en" lang)
      (let [file (open-uri/download-uri! (:looped-doha/audio-url e))]
        (storage/attach! e :looped-doha/audio-attachment file))
      e)))

(defn ingest [f lang]
  (txt/ingest (DohaIngester.) f lang))
