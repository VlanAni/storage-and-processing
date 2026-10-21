(ns clj_1.core)

(defn extend_word [words alphabet] 
  (reduce
    concat
    '()
    (map
      (fn [word]
        (map
          (fn [letter]
            (str word letter))
          (filter
            (fn [letter]
              (not= (str (last word)) letter))
            alphabet)))
      words)))

(defn get_all_words [alphabet N]
  (reduce (fn [words _] (extend_word words alphabet)) '("") (repeat N 0)))