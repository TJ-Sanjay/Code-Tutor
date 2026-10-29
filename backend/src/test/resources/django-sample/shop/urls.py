from django.urls import include, path
from rest_framework.routers import DefaultRouter

from . import api, views

router = DefaultRouter()
router.register(r"categories", api.CategoryViewSet)
router.register(r"products", api.ProductViewSet)
router.register(r"orders", api.OrderViewSet, basename="order")

urlpatterns = [
    path("catalogue/", views.product_list, name="product-list"),
    path("catalogue/<int:pk>/", views.product_detail, name="product-detail"),
    path("catalogue/new/", views.product_create, name="product-create"),
    path("my-orders/", views.OrderHistoryView.as_view(), name="orders"),
    path("checkout/", views.checkout, name="checkout"),
    path("", include(router.urls)),
]
