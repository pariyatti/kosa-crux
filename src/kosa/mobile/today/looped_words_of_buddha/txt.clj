(ns kosa.mobile.today.looped-words-of-buddha.txt
  (:require [clojure.java.io :as io]
            [clojure.set]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [kuti.support.debugging :refer :all]
            [kuti.support.collections :refer [merge-kvs subset-kvs?]]
            [kosa.mobile.today.looped-words-of-buddha.db :as db]
            [kosa.mobile.today.looped.txt :as txt]
            [kuti.support.strings :as strings]
            [kuti.support.digest :as digest])
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
      (URI.)))

(defn shred-cite-block [s]
  (let [cite (str/split s (re-pattern "\n"))]
    #:looped-words-of-buddha{:citation     (get cite 0)
                             :citation-url (URI. (get cite 1))
                             :store-title  (or (get cite 2) "")
                             :store-url    (URI. (or (get cite 3) ""))}))

(defn shred-blocks [lang v]
  (let [all-blocks (str/split (second v) (re-pattern "\n\n"))
        cite-block (shred-cite-block (last all-blocks))
        blocks (drop-last all-blocks)
        audio-url (first blocks)
        translation (->> blocks
                         (drop 1)
                         (str/join "\n\n"))]
    (merge
     #:looped-words-of-buddha{:words (first v)
                              :audio-url (->audio-url audio-url)
                              :translations [[lang translation]]}
     cite-block)))

(defn parse [txt lang]
  (let [marker (get {"en" "Listen"
                     "es" "Escuchar"
                     "fr" "Ecouter " ;; NOTE: yes, it contains a space
                     "it" "Ascolta"
                     "pt" "Ouça"
                     "sr" "Slušaj"
                     "zh" "Listen"} lang)
        m (str marker ": ")]
    (->> (txt/split-file txt)
         (map strings/trim!)
         (map #(shred m %))
         (map #(repair m %))
         (map #(shred-blocks lang %)))))

(defn find-existing [words]
  (first (db/q :looped-words-of-buddha/words (:looped-words-of-buddha/words words))))

(defn db-insert* [words]
  (db/save! (merge #:looped-words-of-buddha
                   {:bookmarkable true
                    :shareable true
                    :original-words (:looped-words-of-buddha/words words)
                    :original-url (URI. "")}
                   words)))

(defn citations [new]
  (if (= "en" (-> new :looped-words-of-buddha/translations first first))
    (select-keys new [:looped-words-of-buddha/citation
                      :looped-words-of-buddha/citation-url
                      :looped-words-of-buddha/store-title
                      :looped-words-of-buddha/store-url])
    {}))

(defn db-insert! [words-of-buddha]
  (if-let [existing (find-existing words-of-buddha)]
    (let [merged (merge-kvs (:looped-words-of-buddha/translations existing)
                            (:looped-words-of-buddha/translations words-of-buddha))]
      (if (= merged (:looped-words-of-buddha/translations existing))
        (log/info (format "Duplicate words ignored: %s" (:looped-words-of-buddha/words words-of-buddha)))
        (do
          (log/info (format "Merging translations: %s" (:looped-words-of-buddha/words words-of-buddha)))
          (db-insert* (merge existing
                             {:looped-words-of-buddha/translations merged}
                             (citations words-of-buddha))))))
    (db-insert* words-of-buddha)))

(defn download-attachments! [e]
  (assoc e :looped-words-of-buddha/audio-attm-id (digest/null-uuid)))

(defn ingest [f lang]
  (log/info (format "Words of Buddha TXT: started ingesting file '%s' for lang '%s'" f lang))
  (let [entries (parse (slurp f) lang)
        entry-count (count entries)]
    (log/info (format "Processing %s Words of Buddha from TXT." entry-count))
    (doseq [[n entry] (map-indexed #(vector %1 %2) entries)]
      (log/info (format "Attempting insert of %s / %s" (+ n 1) entry-count))
      (-> entry
          (download-attachments!)
          (db-insert!))))
  (log/info (format "Words of Buddha TXT: done ingesting file '%s' for lang '%s'" f lang)))
