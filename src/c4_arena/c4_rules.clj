(ns c4-arena.c4-rules
  (:require
   [taoensso.timbre.profiling :refer [defnp]]))

(set! *warn-on-reflection* true)

(def ncols 7)
(def nrows 6)
(def n-to-win 4)

(defnp move-allowed? [state-val move]
  (= 0 (state-val (dec (* (inc move) nrows)))))

(defnp make-move [state-val move side]
  (when-let [i (and
                (<= 0 move (dec ncols))
                (->> (range (* move nrows) (* (inc move) nrows))
                     (filter (fn [i] (= (state-val i) 0)))
                     first))]
    [(assoc state-val i side) i]))

(defnp state-from-moves [moves]
  (->> moves
       (reduce
        (fn [{:keys [state side] :as input} move]
          (if-let [state1 (first (make-move state move side))]
            (let [side1 ({1 2 2 1} side)]
              {:state state1 :side side1})
            input))
        {:state (vec (repeat (* ncols nrows) 0)) :side 1})
       :state))

(defnp states-from-moves [moves]
  (->> moves
       (reductions
        (fn [{:keys [state side] :as input} move]
          (if-let [state1 (first (make-move state move side))]
            (let [side1 ({1 2 2 1} side)]
              {:state state1 :side side1})
            input))
        {:state (vec (repeat (* ncols nrows) 0)) :side 1})
       (map :state)))

(defnp get-winner [state-val i]
  (let [cand (state-val i)]
    (cond
      ;; Check for winner
      (->>
       ;; For all 4 angles
       (for [angle [nrows 1 (- (dec nrows)) (inc nrows)]]
         ;; Count length of
         (count
          (for [dir [:- :+]                     ;; in both directions
                j (reductions + (repeat angle)) ;; straight line
                :let [k ((case dir :+ + :- -) i j)]
                ;; while position is
                :while (and
                        ;; on the board
                        (<= 0 k (dec (* ncols nrows)))
                        ;; hasn't crossed a border
                        (or
                         ;; Sideways
                         (= angle nrows)
                         ;; Angle straight up or slanting up
                         (not=
                          (mod k nrows)
                          (case dir :+ 0 :- (dec nrows))))
                        ;; the symbol as the candidate
                        (= cand (state-val k)))]
            true)))
       ;; At least one line is longer than needed to win
       (some (fn [n] (>= n (dec n-to-win)))))
      (dec cand)
      ;; Check if there is no remaining space on the board
      (not-any? #{0} state-val) (dec 0))))
