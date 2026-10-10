/* Signed Cloudinary uploads via the protected Spring MVC endpoint; URL is then saved by normal form. */
document.querySelectorAll('[data-cloudinary-upload]').forEach(picker => {
  picker.addEventListener('change', async () => {
    const file = picker.files?.[0];
    if (!file) return;
    const form = picker.closest('form');
    const destination = document.getElementById(picker.dataset.cloudinaryTarget);
    const preview = document.getElementById(picker.dataset.cloudinaryPreview);
    const status = form.querySelector('[data-upload-status]');
    const submit = form.querySelector('button[type="submit"], button.button:not([type])');
    if (!destination) return;
    if (file.size > 2 * 1024 * 1024) {
      if (status) status.textContent = 'Image must be smaller than 2 MB.';
      return;
    }
    const data = new FormData();
    data.append('file', file);
    data.append('kind', picker.dataset.cloudinaryKind || 'foods');
    const csrf = form.querySelector('input[name="_csrf"]');
    if (csrf) data.append(csrf.name, csrf.value);
    if (submit) submit.disabled = true;
    if (status) status.textContent = 'Uploading to Cloudinary...';
    try {
      const response = await fetch('/seller/uploads/images', {
        method: 'POST', body: data, credentials: 'same-origin'
      });
      if (!response.ok) throw new Error(`Upload failed (HTTP ${response.status}). Check Cloudinary configuration and image type.`);
      const result = await response.json();
      if (!result.url || !result.url.startsWith('https://res.cloudinary.com/')) throw new Error('Invalid upload response.');
      destination.value = result.url;
      destination.dispatchEvent(new Event('input', { bubbles: true }));
      if (preview) preview.src = result.url;
      if (status) status.textContent = 'Image uploaded. Save the form to apply changes.';
    } catch (error) {
      if (status) status.textContent = error.message;
    } finally {
      if (submit) submit.disabled = false;
    }
  });
});
