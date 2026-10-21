// The bounce page of QRST §11.2a. It reads its own fragment, which never reached the
// server, and offers to hand it to the app. It acts on nothing itself.
(function () {
  if (window.top !== window.self) return; // never inside another page's frame
  var hash = location.hash.slice(1);
  if (!/(^|&)npub=npub1/.test(hash) || !/(^|&)v=/.test(hash)) return;
  var params = new URLSearchParams(hash);
  var mode = params.get('mode');
  var profile = params.get('p') || '';
  var link = location.href;

  var what = profile === 'nostr-nsec' ? 'a Nostr key' : profile === 'frost-share' ? 'a FROST share' : '“' + profile + '”';
  document.getElementById('what').textContent =
    mode === 'offer' ? 'Scanning this makes your phone send ' + what : 'Scanning this lets your phone receive ' + what;
  document.getElementById('detail').textContent =
    mode === 'offer'
      ? 'The device showing this code wants to receive ' + what + '. The app will ask you to type the digits that device shows before anything is sent.'
      : 'The device showing this code is sending ' + what + '. The app will show digits to type on that device.';

  // The qrst: handoff of QRST §11.2c: the same parameters with the key as the host, for
  // whichever installed QRST app claims the scheme. Followed only when the user taps it.
  var npub = params.get('npub');
  var rest = hash.split('&').filter(function (part) { return part.indexOf('npub=') !== 0; }).join('&');
  var handoff = npub + '?' + rest;
  document.getElementById('open').href = /Android/i.test(navigator.userAgent)
    ? 'intent://' + handoff + '#Intent;scheme=qrst;S.browser_fallback_url=' +
      encodeURIComponent('https://github.com/sybenx/qrst-signer/releases') + ';end'
    : 'qrst://' + handoff;

  document.getElementById('copy').addEventListener('click', function () {
    navigator.clipboard.writeText(link).then(function () { document.getElementById('copied').hidden = false; });
  });
  document.getElementById('pairing').hidden = false;
})();
