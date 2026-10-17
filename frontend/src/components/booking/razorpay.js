// Razorpay Standard Checkout (https://razorpay.com/docs/payments/payment-gateway/web-integration/standard/).
// The script is loaded on demand from Razorpay's CDN; only the public key id ever reaches the browser.
const CHECKOUT_SRC = 'https://checkout.razorpay.com/v1/checkout.js';
let loading = null;

export function loadRazorpay() {
  if (window.Razorpay) return Promise.resolve(true);
  if (loading) return loading;
  loading = new Promise((resolve) => {
    const script = document.createElement('script');
    script.src = CHECKOUT_SRC;
    script.async = true;
    script.onload = () => resolve(!!window.Razorpay);
    script.onerror = () => {
      loading = null;
      resolve(false);
    };
    document.body.appendChild(script);
  });
  return loading;
}

/**
 * Opens Checkout for a server-created order. Exactly one callback fires per attempt outcome:
 * onSuccess(response) with razorpay_payment_id / razorpay_order_id / razorpay_signature (to be verified
 * by the backend, never trusted as-is), onFailure(error) for a failed attempt (the popup stays open so
 * the guest can retry), onDismiss() when the guest closes the popup.
 */
export function openCheckout(order, { onSuccess, onFailure, onDismiss }) {
  const rzp = new window.Razorpay({
    key: order.keyId,
    order_id: order.orderId,
    amount: order.amount,
    currency: order.currency,
    name: order.name,
    description: order.description,
    prefill: { name: order.customerName, email: order.customerEmail },
    theme: { color: '#ff385c' },
    handler: onSuccess,
    modal: { ondismiss: onDismiss, confirm_close: true },
  });
  rzp.on('payment.failed', (resp) => onFailure(resp.error || {}));
  rzp.open();
  return rzp;
}
