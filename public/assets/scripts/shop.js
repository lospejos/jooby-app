SHOP = {

    MOBILE_MAX_RESOLUTION: 850,

    bindBannerClick: function() {
        //banner click
        $('.banner img').click(function() {
            var promoCategoryId = $(this).attr('data-promo');
            if (!promoCategoryId) return true;
            var el = $('#' + promoCategoryId);
            SHOP.scrollToElement(el);
            return false;
        });
    },

    bindPopupEvents: function() {
        $('.cd-popup-trigger').on('click', function(event) {
            event.preventDefault();

            $(".add-to-cart input[type='checkbox']").prop("checked", false );
            var id = $(event.target).attr('data-product-id');
            $(".add-to-cart input[name='productId']").val(id);
            $(".add-to-cart input[name='quantity']").val(1);
            $(".add-to-cart .popup-row").attr('id', id);
            $(".add-to-cart .product-name").text($(event.target).attr('data-product-name'));
            $(".add-to-cart .number").text($(event.target).attr('data-unit-coefficient'));
            $(".add-to-cart .unit").text($(event.target).attr('data-unit-label'));
            var price = $(event.target).attr('data-price');
            $(".add-to-cart .price-value").text(price);
            $(".add-to-cart .popup-row").attr('data-price', price);
            $(".add-to-cart .popup-row").attr('data-coefficient', $(event.target).attr('data-unit-coefficient'));
            $('.cd-popup.add-to-cart').addClass('is-visible');
        });

        $('.cart-popup-trigger').on('click', function(event) {
            event.preventDefault();
            if ($(this).hasClass('disabled')) {
                return;
            }
            SHOP.showCart();
        });

        //close popup
        $('.cd-popup').on('click', function(event) {
            if ($(event.target).is('.cd-popup-close img') || $(event.target).is('.cd-popup') ) {
                event.preventDefault();
                $(this).removeClass('is-visible');
            }
        });
        //close popup when clicking the esc keyboard button
        $(document).keyup(function(event) {
            if(event.which == '27'){
                $('.cd-popup').removeClass('is-visible');
            }
        });

        $('.add-to-cart-js .spinner .btn').on('click', function(event) {
            var row = $(this).parent().parent();
            var productId = row.attr('id');
            var coefficient = Number(row.attr('data-coefficient'));
            var quantity = $(".add-to-cart-js input[name='quantity']").val();
            var res = Number(quantity) + ($(this).hasClass('btn-plus')? 1 : -1);
            if (res === 0)
                res = 1;
            var quantitySpan = $('#' + productId + ' .quantity .number');
            quantitySpan.text(res * coefficient);
            $(".add-to-cart-js input[name='quantity']").val(res);
            var price = row.attr('data-price');
            $('#' + productId + ' .price-value').text(price * res);
        });

        $('.box label').on('click', function(event) {
            var cb = $('input#' + $(this).attr('for') + '[type="checkbox"]');
            cb.attr('checked', !cb.attr('checked'));
        });

        $(".product-detail-description input[type='checkbox']").prop("checked", false );
    },

    showCart: function() {
        $('.loading-indicator').show();

        if ($(window).width() <= SHOP.MOBILE_MAX_RESOLUTION) {
            window.location = $('#rootPath').val() + '/m-cart';
            return;
        }
        if ($('#cartContentTemplateDiv').length === 0) {
            $('body').append("<div id='cartContentTemplateDiv'/>");
        }
        $("#cartContentTemplateDiv").load("/assets/views/cart.html", function (response, status, xhr) {
            if (status == "error") {
                console.log("Error loading cart template" + xhr.status + " " + xhr.statusText);
                return;
            }
            $.ajax({
                type: 'GET',
                url: '/cart',
                success: function (result) {
                    if (result.totalCount === 0) {
                        return;
                    }
                    var source = $("#cartContentTemplate").html();
                    var template = Handlebars.compile(source);
                    $('.cd-popup.cart').html(template(result));
                    $(".cd-popup.cart .description" ).each(function( index ) {
                        $(this).html($(this).text());
                    });

                    var container = $('.cart .cd-popup-container');
                    var offset = ($(window).height() - container.height()) / 2;
                    container.css('margin-top', offset + 'px');
                    container.css('margin-bottom', offset + 'px');
                    if (($(window).height() - container.height()) / $(window).height() < 0.12) {
                        container.css('margin-top', '4%');
                        container.css('margin-bottom', '4%');
                        container.css('height', '85%');
                        $('.cart-entries').css('max-height', (container.height() - 200) + 'px');
                    }
                    $(".cd-popup.cart .description" ).each(function( index ) {
                        $(this).html($(this).text());
                        $(this).css('display', 'block');
                    });
                    $('.cd-popup.cart').addClass('is-visible');
                    $('.cart .spinner .btn').on('click', SHOP.modifyCart);
                }
            }).always(function () {
                $('.loading-indicator').hide();
            });
        });
    },

    updateCartTotal: function() {
        $.ajax({
            type: 'GET',
            url: '/cart',
            success: function (result) {
                $(".cart-total").text(result.totalPrice);
                if (result.totalPrice > 0) {
                    $(".basket-responsive").removeClass('disabled');
                    $(".basket-responsive .cart-total").css('display', 'inline');
                    $(".basket-responsive .cart-total").html(result.totalPrice + '&#8381;');
                } else {
                    $(".basket-responsive").addClass('disabled');
                    $(".basket-responsive .cart-total").css('display', 'none');
                }
            }
        });
    },

    modifyCart: function(event) {
        var row = $(this).parent().parent();
        if (!row.hasClass('cart-row'))
            row = row.parent();
        var productId = row.attr('data-product-id');
        var entryNo = row.attr('data-entry-no');
        var coefficient = Number(row.attr('data-unit-coefficient'));
        var quantitySpan = $('div[data-product-id="' + productId + '"] .quantity .number');
        var quantity = Number(quantitySpan.text()) + ($(this).hasClass('btn-plus')? coefficient : -coefficient);
        if (quantity === 0) {
            removeFromCart(entryNo);
            return;
        }
        $('.loading-indicator').show();
        $.ajax({
            type: 'PUT',
            url: '/cart',
            data: {entryNo: entryNo, quantity: (quantity / coefficient)},
            success: function (result) {
                //todo: optimize double call
                SHOP.updateCartTotal();
                SHOP.showCart();
            },
            error: function (xhr, str) {
                console.log('Возникла ошибка: ' + xhr.responseCode);
            }
        }).always(function() {

        });
    },

    formatDescriptions: function() {
        $(".description" ).each(function( index ) {
            $(this).html($(this).text());
            $(this).css('display', 'block');
        });
        $(".review-wrapper div" ).each(function( index ) {
            $(this).html($(this).text());
            $(this).css('display', 'inline');
        });
        $(".cms-content" ).each(function( index ) {
            $(this).html($(this).text());
            $(this).css('display', 'block');
        });
    },

    bindFixedMenu: function() {
        $(window).scroll(function(){
            var elem = $('.fixed-nav-bar');
            if ($(window).width() <= SHOP.MOBILE_MAX_RESOLUTION) {
                elem.css('display', 'none');
                return;
            }
            var top = $(this).scrollTop();
            var header = $('header');
            var nav = $('.main-navigation');
            if (top < header.outerHeight() + nav.height()) {
                elem.css('display', 'none');
            } else {
                elem.css('display', 'block');
            }
        });
    },

    scrollToElement: function(el) {
        var additionalShift = 0;
        if ($(window).width() <= SHOP.MOBILE_MAX_RESOLUTION) {
            additionalShift = $('header').outerHeight() + 5;
        } else {
            additionalShift = $('.fixed-nav-bar').outerHeight() + 5;
        }
        $('body,html').animate(
            {scrollTop: (el.offset().top - additionalShift)},
            {duration: 1500, easing: "swing"}
        );
        var menu = $('.topnav-responsive');
        if (menu.hasClass('expanded'))
            menu.removeClass('expanded');
    },

    bindScrolls: function() {
        $('a[href*="#"]:not(.backtotop):not(.cd-popup-trigger)').click(function(e) {
            var el = $('#' + $(this).attr('href').split('#')[1]);
            SHOP.scrollToElement(el);
            return false;
        });
        $('.menu-row').click(function(e) {
            var link = $(this).find('a[href*="#"]');
            if (link.length == 0) {
                window.location = $(this).find('a').first().attr('href');
                return false;
            }
            var el = $('#' + link.first().attr('href').split('#')[1]);
            if (el.length == 0) {
                window.location = link.first().attr('href');
                return false;
            }
            SHOP.scrollToElement(el);
            return false;
        });

        //backtotop
        $('.backtotop').click(function(){
           $('html, body').animate({scrollTop:0}, 'slow');
        });
        var scrollDiv=$('a.backtotop');
        $(window).scroll(function() {
            if($(window).scrollTop() == "0") {
                $(scrollDiv).fadeOut("slow");
            } else {
                $(scrollDiv).fadeIn("slow");
            }
        });
        $(scrollDiv).hide();
    },

    adjustMainMarginMobile: function() {
        if ($(window).outerWidth() <= SHOP.MOBILE_MAX_RESOLUTION) {
            $('.main').css('margin-top', $('header').outerHeight());
        }
        $(window).resize(function () {
            if ($(window).outerWidth() <= SHOP.MOBILE_MAX_RESOLUTION) {
                $('.main').css('margin-top', $('header').outerHeight());
            } else {
                $('.main').css('margin-top', '0');
            }
        });
    },

    bindSliders: function() {
        $('.reviews-slider').slick({
           dots: true,
           infinite: true,
           speed: 300,
           slidesToShow: 3,
           slidesToScroll: 3,
           responsive: [
             {
               breakpoint: SHOP.MOBILE_MAX_RESOLUTION,
               settings: {
                 slidesToShow: 1,
                 slidesToScroll: 1
               }
             }
           ]
        });
    }
}

$(document).ready(function () {
    SHOP.bindBannerClick();
    SHOP.formatDescriptions();
    SHOP.bindPopupEvents();
    SHOP.bindScrolls();
    SHOP.bindFixedMenu();
    $('.mobile-cart .spinner .btn').on('click', SHOP.modifyCart);
    SHOP.bindSliders();
});

$(window).on("load", function () {
    SHOP.adjustMainMarginMobile();
    if (window.location.hash && $(window.location.hash).length > 0) {
        SHOP.scrollToElement($(window.location.hash));
    }
});

function removeFromCart(entryNo) {
    $('.loading-indicator').show();
    $.ajax({
        type: 'DELETE',
        url: '/cart?entryNo=' + entryNo,
        error: function (xhr, str) {
            console.log('Возникла ошибка: ' + xhr.responseCode);
        }
    }).always(function () {
        $('.cd-popup.cart').removeClass('is-visible');
        $('.loading-indicator').hide();
        //todo: optimize double call
        SHOP.updateCartTotal();
        SHOP.showCart();
    });
}

function addAndPlace() {
    addToCart(true);
}

function addAndContinue() {
    addToCart(false);
}

function addToCart(openCart) {
    $('.cd-popup.add-to-cart').removeClass('is-visible');
    $('.loading-indicator').show();

    var msg = $('.add-to-cart-js form').serializeArray();
    var obj = {additions: []};
    for(var i in msg) {
        var fieldName = msg[i].name;
        if (fieldName == 'additions') {
            obj.additions.push(msg[i].value);
        } else {
            obj[fieldName] = msg[i].value;
        }
    }
    if (isNaN(obj.quantity)) {
        return;
    }
    $.ajax({
        type: 'POST',
        url: '/cart',
        data: obj,
        error: function (xhr, str) {
            console.log('Возникла ошибка: ' + xhr.responseCode);
        }
    }).always(function () {
        SHOP.updateCartTotal();
        $('.loading-indicator').hide();
        if (openCart) {
            SHOP.showCart();
        }
    });
}

function changeInputValue(id, delta) {
    var input = $('#' + id);
    var val = input.val();
    try {
        val = parseInt(val.match(/\d+/)[0]) + delta;
    } catch (err) {
        val = NaN;
    }
    if (val < 1 || isNaN(val))
        val = 1;
    val = val + " кг";
    input.val(val);
}


function menuClick() {
    var menu = $('.topnav-responsive');
    menu.css('top', $('header').outerHeight());

    if (menu.hasClass('expanded'))
        menu.removeClass('expanded');
    else
        menu.addClass('expanded');
}





