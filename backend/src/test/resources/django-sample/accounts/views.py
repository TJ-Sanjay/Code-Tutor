from django.contrib.auth import authenticate, login, logout
from django.shortcuts import redirect, render
from django.views.decorators.csrf import csrf_exempt
from django.views.decorators.http import require_http_methods

from .models import User


@require_http_methods(["GET", "POST"])
def login_view(request):
    """Shows the login form and signs the user in."""
    if request.method == "POST":
        user = authenticate(request, username=request.POST.get("username"), password=request.POST.get("password"))
        if user is not None:
            login(request, user)
            return redirect("/")
    return render(request, "accounts/login.html")


def logout_view(request):
    logout(request)
    return redirect("/")


@csrf_exempt
def register(request):
    user = User.objects.create_user(username=request.POST["username"], password=request.POST["password"])
    return render(request, "accounts/welcome.html", {"user": user})
