from django.db import models


class ProductQuerySet(models.QuerySet):
    def in_stock(self):
        return self.filter(stock__gt=0)

    def by_category(self, category):
        return self.filter(category=category)
