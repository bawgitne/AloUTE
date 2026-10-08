/**
 * ALOUTE — Post Composer
 * Handles quick post creation & modal post creation
 */

document.addEventListener('DOMContentLoaded', () => {
  const postForm = document.getElementById('create-post-form');
  const quickInput = document.getElementById('quick-post-input');
  const modalEl = document.getElementById('createPostModal');
  let createModal = null;

  if (modalEl && window.bootstrap) {
    createModal = new window.bootstrap.Modal(modalEl);
  }

  // Quick Post Input Click -> Opens Modal
  if (quickInput) {
    quickInput.addEventListener('click', () => {
      if (createModal) {
        createModal.show();
        setTimeout(() => {
          document.getElementById('post-modal-content')?.focus();
        }, 300);
      }
    });
  }

  // Submit Handler for Create Post
  if (postForm) {
    postForm.addEventListener('submit', async (e) => {
      e.preventDefault();

      const contentInput = document.getElementById('post-modal-content');
      const visibilitySelect = document.getElementById('post-modal-visibility');
      const topicsInput = document.getElementById('post-modal-topics');
      const submitBtn = postForm.querySelector('button[type="submit"]');

      const content = contentInput?.value?.trim();
      if (!content) {
        window.Aloute.toast.show('Nội dung bài viết không được để trống', 'warning');
        return;
      }

      const visibility = visibilitySelect?.value || 'PUBLIC';
      const topicsRaw = topicsInput?.value?.trim();
      const topicNames = topicsRaw ? topicsRaw.split(',').map(t => t.trim().replace(/^#/, '')).filter(Boolean) : [];

      submitBtn.disabled = true;
      submitBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Đang đăng...';

      try {
        const payload = {
          content,
          visibility,
          topicNames,
          mediaKeys: []
        };

        const createdPost = await window.httpClient.post('/api/posts', payload);
        window.Aloute.toast.show('Đăng bài viết thành công!', 'success');

        if (createModal) {
          createModal.hide();
        }
        postForm.reset();

        // Reload feed smoothly or reload page
        setTimeout(() => {
          window.location.reload();
        }, 600);
      } catch (err) {
        console.error('Post creation failed:', err);
        if (err.status === 401) {
          window.Aloute.toast.show('Vui lòng đăng nhập để đăng bài', 'warning');
        } else {
          window.Aloute.toast.show(err.message || 'Không thể tạo bài viết', 'danger');
        }
      } finally {
        submitBtn.disabled = false;
        submitBtn.innerHTML = '<i class="fa-solid fa-paper-plane me-1"></i> Đăng bài';
      }
    });
  }
});
