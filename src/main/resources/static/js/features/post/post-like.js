/**
 * ALOUTE — Post Interactions (Like, Bookmark, Repost)
 */

document.addEventListener('DOMContentLoaded', () => {
  // Use event delegation for dynamically loaded posts or static posts
  document.body.addEventListener('click', async (event) => {
    // 1. Like Button
    const likeBtn = event.target.closest('.btn-like');
    if (likeBtn) {
      event.preventDefault();
      const postId = likeBtn.getAttribute('data-post-id');
      const countEl = likeBtn.querySelector('.like-count');
      const iconEl = likeBtn.querySelector('i');
      
      const isLiked = likeBtn.classList.contains('liked');
      let count = parseInt(countEl?.textContent || '0', 10);

      // Optimistic UI update
      likeBtn.classList.toggle('liked', !isLiked);
      if (iconEl) {
        iconEl.className = !isLiked ? 'fa-solid fa-heart text-danger' : 'fa-regular fa-heart';
      }
      if (countEl) {
        countEl.textContent = !isLiked ? count + 1 : Math.max(0, count - 1);
      }

      try {
        const res = await window.httpClient.post(`/api/posts/${postId}/like`);
        if (res) {
          likeBtn.classList.toggle('liked', res.liked);
          if (iconEl) {
            iconEl.className = res.liked ? 'fa-solid fa-heart text-danger' : 'fa-regular fa-heart';
          }
          if (countEl && typeof res.likeCount === 'number') {
            countEl.textContent = res.likeCount;
          }
        }
      } catch (err) {
        // Rollback optimistic update
        likeBtn.classList.toggle('liked', isLiked);
        if (iconEl) {
          iconEl.className = isLiked ? 'fa-solid fa-heart text-danger' : 'fa-regular fa-heart';
        }
        if (countEl) countEl.textContent = count;
        
        if (err.status === 401) {
          window.Aloute.toast.show('Vui lòng đăng nhập để thích bài viết', 'warning');
        } else {
          window.Aloute.toast.show(err.message || 'Không thể thao tác', 'danger');
        }
      }
      return;
    }

    // 2. Bookmark Button
    const bookmarkBtn = event.target.closest('.btn-bookmark');
    if (bookmarkBtn) {
      event.preventDefault();
      const postId = bookmarkBtn.getAttribute('data-post-id');
      const iconEl = bookmarkBtn.querySelector('i');
      const isBookmarked = bookmarkBtn.classList.contains('bookmarked');

      // Optimistic update
      bookmarkBtn.classList.toggle('bookmarked', !isBookmarked);
      if (iconEl) {
        iconEl.className = !isBookmarked ? 'fa-solid fa-bookmark text-warning' : 'fa-regular fa-bookmark';
      }

      try {
        if (!isBookmarked) {
          await window.httpClient.put(`/api/posts/${postId}/bookmarks/me`);
          window.Aloute.toast.show('Đã lưu bài viết', 'success');
        } else {
          await window.httpClient.delete(`/api/posts/${postId}/bookmarks/me`);
          window.Aloute.toast.show('Đã bỏ lưu bài viết', 'info');
        }
      } catch (err) {
        bookmarkBtn.classList.toggle('bookmarked', isBookmarked);
        if (iconEl) {
          iconEl.className = isBookmarked ? 'fa-solid fa-bookmark text-warning' : 'fa-regular fa-bookmark';
        }
        if (err.status === 401) {
          window.Aloute.toast.show('Vui lòng đăng nhập để lưu bài viết', 'warning');
        } else {
          window.Aloute.toast.show(err.message || 'Không thể thao tác', 'danger');
        }
      }
      return;
    }

    // 3. Repost Button
    const repostBtn = event.target.closest('.btn-repost');
    if (repostBtn) {
      event.preventDefault();
      const postId = repostBtn.getAttribute('data-post-id');
      const countEl = repostBtn.querySelector('.repost-count');
      const isReposted = repostBtn.classList.contains('reposted');
      let count = parseInt(countEl?.textContent || '0', 10);

      repostBtn.classList.toggle('reposted', !isReposted);
      if (countEl) countEl.textContent = !isReposted ? count + 1 : Math.max(0, count - 1);

      try {
        const res = await window.httpClient.post(`/api/posts/${postId}/repost`);
        if (res && countEl && typeof res.repostCount === 'number') {
          countEl.textContent = res.repostCount;
        }
        window.Aloute.toast.show(!isReposted ? 'Đã đăng lại bài viết' : 'Đã hủy đăng lại', 'success');
      } catch (err) {
        repostBtn.classList.toggle('reposted', isReposted);
        if (countEl) countEl.textContent = count;
        if (err.status === 401) {
          window.Aloute.toast.show('Vui lòng đăng nhập để đăng lại', 'warning');
        } else {
          window.Aloute.toast.show(err.message || 'Không thể đăng lại', 'danger');
        }
      }
      return;
    }

    // 4. Delete Post Option
    const deleteBtn = event.target.closest('.btn-delete-post');
    if (deleteBtn) {
      event.preventDefault();
      const postId = deleteBtn.getAttribute('data-post-id');
      if (confirm('Bạn có chắc chắn muốn xóa bài viết này không?')) {
        try {
          await window.httpClient.delete(`/api/posts/${postId}`);
          window.Aloute.toast.show('Đã xóa bài viết thành công', 'success');
          const postCard = document.getElementById(`post-card-${postId}`);
          if (postCard) {
            postCard.style.transition = 'opacity 0.3s ease, transform 0.3s ease';
            postCard.style.opacity = '0';
            postCard.style.transform = 'scale(0.95)';
            setTimeout(() => postCard.remove(), 300);
          }
        } catch (err) {
          window.Aloute.toast.show(err.message || 'Không thể xóa bài viết', 'danger');
        }
      }
    }
  });
});
