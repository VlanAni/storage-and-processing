(ns clj_3.core)

(defn- take-blocks
  [n block-size coll]
  (loop [i 0, s coll, blocks []]
    (if (or (= i n) (not (seq s)))
      [blocks s]
      (let [[block more] (split-at block-size s)]
        (recur (inc i) more (conj blocks block))))))

(defn p-filter
  ([pred coll] (p-filter pred 4 100 coll))
  ([pred n block-size coll]
   {:pre [(pos? n) (pos? block-size)]}
   (lazy-seq
     (loop [s coll]
       (when (seq s)
         (let [[blocks more] (take-blocks n block-size s)
               futs   (doall (map (fn [block] (future (doall (filter pred block))))
                                  blocks))
               result (doall (mapcat deref futs))]
           (if (seq result)
             (concat result (p-filter pred n block-size more))
             (recur more))))))))