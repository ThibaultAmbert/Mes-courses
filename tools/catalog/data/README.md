# Données du catalogue

- `catalogue-rationalise-v2.jsonl.gz` : base consolidée fournie le 14/11/2026 puis filtrée sans déduction de type (139 585 fiches issues de 9 sites avec catégorie site reconnue ; `picnic` et `cuisineaddict` sont sans catégorie dans cet export initial et alimentés au crawl).
- Consommée par le workflow `catalog-import.yml` via la commande `split` de l'outil `tools/catalog`.
- Ce fichier peut être retiré du dépôt après la première publication du catalogue.
