(ns codegumi.buffer)

; Atoms to hold our photos and listeners
(def plist (atom []))
(def destroy-listeners (atom []))
(def add-listeners (atom []))

(defn register-listener [list f]
  "Put a listener function on an atom"
  (swap! list conj f))

(defn add-item [i]
  "Add an item to the atom and call listeners on it"
  (doseq [f @add-listeners] (f i))
  (swap! plist conj i))

(defn remove-item []
  "Remove item from atom. We use reset! to make this work"
  (doseq [f @destroy-listeners] (f (first @plist)))
  (reset! plist (vec (rest @plist))))

(defn create-buffer [size]
  "Return a function that keeps plist at size when adding"
  (fn [item]
    (add-item item)
    (when (> (count @plist) size)
      (remove-item))))
