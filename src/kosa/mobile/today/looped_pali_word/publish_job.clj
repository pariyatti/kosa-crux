(ns kosa.mobile.today.looped-pali-word.publish-job
  (:require [clojure.tools.logging :as log]
            [kosa.mobile.today.looped-pali-word.db :as loop-db]
            [kosa.mobile.today.pali-word.db :as pali-db]
            [kuti.support.types :as types]
            [kuti.support.time :as time]))

;; (def looped-card-count 220)
;; (def days-since-epoch (t/days (t/between (t/epoch) (t/now))))
;; (def days-since-perl (- days-since-epoch 12902))
;; (def todays-word (mod days-since-perl looped-card-count))

(defn which-card [today card-count]
  ;; "perl epoch":
  ;; (t/>> (t/epoch)
  ;;       (t/new-duration 12902 :days))
  ;; => #time/instant "2005-04-29T00:00:00Z"
  (mod (time/days-between "2005-04-29T00:00:00Z" today)
       card-count))

(defn run-job! [_]
  (log/info "#### Running looped pali word publish job")
  (let [n (count (loop-db/list))]
    (if (< 0 n)
      (let [idx (which-card (time/now) n)
            word (-> (loop-db/q :looped-pali-word/index idx)
                     first
                     (types/dup :pali-word))]
        (log/info (str "#### Today's pali word is: " (:pali-word/pali word)))
        (pali-db/save! word))
      (log/info "#### Zero looped pali words found."))))