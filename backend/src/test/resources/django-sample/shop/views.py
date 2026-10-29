from django.contrib.auth.decorators import login_required
from django.contrib.auth.mixins import LoginRequiredMixin
from django.shortcuts import get_object_or_404, render
from django.views.generic import ListView

from .forms import ProductForm
from .models import Product
from .services import OrderService


def product_list(request):
    """Public catalogue page with optional search."""
    query = request.GET.get("q", "")
    products = Product.objects.in_stock().filter(name__icontains=query)
    return render(request, "shop/product_list.html", {"products": products})


@login_required
def product_detail(request, pk):
    product = get_object_or_404(Product, pk=pk)
    return render(request, "shop/product_detail.html", {"product": product})


@login_required
def product_create(request):
    if request.method == "POST":
        form = ProductForm(request.POST)
        if form.is_valid():
            form.save()
    return render(request, "shop/product_form.html")


class OrderHistoryView(LoginRequiredMixin, ListView):
    template_name = "shop/orders.html"
    model = Product


def checkout(request):
    service = OrderService()
    service.place_order(request.user, [])
    return render(request, "shop/done.html")
