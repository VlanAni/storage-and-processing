(ns clj_2.core)

(defn sieve [numbers]
  (lazy-seq
    (if (seq numbers)
      (let [prime (first numbers)] (cons
        prime
        (sieve
            (remove
            (fn [x] (zero? (mod x prime)))
            (rest numbers)))))
      nil
      )))

(defn integers-from [n]
  (lazy-seq
    (cons n
          (integers-from (inc n)))))

(def primes (sieve (integers-from 2)))

