(ns joplin.migrators.crux.20210406174427-add-pali-word
  (:require [joplin.crux.database :as d]
            [kuti.record.schema :as schema]))

(defn up [db]
  (let [node (d/get-node (:conf db))]
    (schema/add-type node :pali-word [:pali-word/original-pali  ;; from *.pariyatti.org - a long string
                                      :pali-word/original-url   ;; from *.pariyatti.org
                                      :pali-word/bookmarkable
                                      :pali-word/shareable
                                      :pali-word/pali
                                      :pali-word/translations
                                      :pali-word/published-at])
    (schema/add-schema node :pali-word/original-pali :db.type/string)
    (schema/add-schema node :pali-word/original-url  :db.type/uri)
    (schema/add-schema node :pali-word/bookmarkable  :db.type/boolean)
    (schema/add-schema node :pali-word/shareable     :db.type/boolean)
    (schema/add-schema node :pali-word/pali          :db.type/string)
    (schema/add-schema node :pali-word/translations  :db.type/tuple)
    (schema/add-schema node :pali-word/published-at  :db.type/inst)
    (d/close!)))

(defn down [db]
  (let [node (d/get-node (:conf db))]
    (schema/remove-type node :pali-word)
    (schema/remove-schema node :pali-word/original-pali)
    (schema/remove-schema node :pali-word/original-url)
    (schema/remove-schema node :pali-word/bookmarkable)
    (schema/remove-schema node :pali-word/shareable)
    (schema/remove-schema node :pali-word/pali)
    (schema/remove-schema node :pali-word/translations)
    (schema/remove-schema node :pali-word/published-at)
    (d/close!)))
