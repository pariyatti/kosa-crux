(ns kosa.api.handler
  (:require [kuti.support.time :as time]
            [kuti.support.digest :refer [uuid]]
            [kosa.library.artefacts.image.db :as image-db]
            [kosa.mobile.today.pali-word.db :as pali-word-db]
            [kosa.mobile.today.stacked-inspiration.db :as stacked-inspiration-db]
            [ring.util.response :as resp]))

(defn search [req]
  (let [text (-> req :params :q)
        list (image-db/search-for text)]
    (resp/response
     list)))

(def kosa-epoch "2020-12-12T00:00:00.000Z")

(defn pali-word->json [word]
  ;; TODO: how much of the Crux entity do we just want to hand over verbatim?
  (let [published (:pali-word/published-at word)
        date (time/string (or published kosa-epoch))]
    {:type "pali_word"
     :id (:crux.db/id word)
     :published_at date
     :created_at date
     :updated_at date
     :header "Pāli Word of the Day"
     :bookmarkable true
     :shareable true
     :pali (:pali-word/pali word)
     :translations (map (fn [t] {:id (uuid)
                                 :language (first t)
                                 :translation (second t)})
                        (:pali-word/translations word))
     :audio {:url (format "/uploads/cards/pali_word_card/audio/%s" (:crux.db/id word))}}))

(defn stacked-inspiration->json [card]
  (let [published (:stacked-inspiration/published-at card)
        date (time/string (or published kosa-epoch))]
    {:type "stacked_inspiration"
     :id (:crux.db/id card)
     :published_at date
     :created_at date
     :updated_at date
     :header "Inspiration"
     :bookmarkable true
     :shareable true
     :text (:stacked-inspiration/text card)
     :image {:url (-> card :stacked-inspiration/image-attachment :url)}}))

(defn today-list []
  (vec (concat
        (map pali-word->json (pali-word-db/list))
        (map stacked-inspiration->json (stacked-inspiration-db/list)))))

(defn today [req]
  (resp/response (today-list)))
