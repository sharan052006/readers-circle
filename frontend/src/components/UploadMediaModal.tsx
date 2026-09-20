import React, { useEffect, useRef, useState } from 'react';

interface UploadMediaModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (data: { mediaType: 'PHOTO' | 'VIDEO'; file: File; caption?: string }) => Promise<void>;
  eventTitle: string;
}

const MAX_FILE_SIZE_BYTES = 50 * 1024 * 1024; // 50MB

const ALLOWED_PHOTO_EXTS = ['jpg', 'jpeg', 'png', 'webp'];
const ALLOWED_VIDEO_EXTS = ['mp4', 'webm', 'mov', 'mkv'];

export const UploadMediaModal: React.FC<UploadMediaModalProps> = ({
  isOpen,
  onClose,
  onSubmit,
  eventTitle,
}) => {
  const [mediaType, setMediaType] = useState<'PHOTO' | 'VIDEO'>('PHOTO');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [caption, setCaption] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const fileInputRef = useRef<HTMLInputElement>(null);

  // Clean up object URLs when preview changes or component unmounts
  useEffect(() => {
    return () => {
      if (previewUrl) {
        URL.revokeObjectURL(previewUrl);
      }
    };
  }, [previewUrl]);

  // Reset file selection when switching between Photo and Video
  const handleMediaTypeChange = (type: 'PHOTO' | 'VIDEO') => {
    if (type !== mediaType) {
      if (previewUrl) {
        URL.revokeObjectURL(previewUrl);
        setPreviewUrl(null);
      }
      setSelectedFile(null);
      setError('');
      setMediaType(type);
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
    }
  };

  if (!isOpen) return null;

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setError('');
    const file = e.target.files?.[0];
    if (!file) return;

    // Validate file size (50MB)
    if (file.size > MAX_FILE_SIZE_BYTES) {
      setError(`File size (${(file.size / (1024 * 1024)).toFixed(1)} MB) exceeds the 50MB limit.`);
      if (fileInputRef.current) fileInputRef.current.value = '';
      return;
    }

    // Validate file extension
    const ext = file.name.split('.').pop()?.toLowerCase() || '';
    if (mediaType === 'PHOTO') {
      const isImage = ALLOWED_PHOTO_EXTS.includes(ext) || file.type.startsWith('image/');
      if (!isImage) {
        setError(`Invalid image format (.${ext}). Supported formats: JPG, JPEG, PNG, WEBP.`);
        if (fileInputRef.current) fileInputRef.current.value = '';
        return;
      }
    } else {
      const isVideo = ALLOWED_VIDEO_EXTS.includes(ext) || file.type.startsWith('video/');
      if (!isVideo) {
        setError(`Invalid video format (.${ext}). Supported formats: MP4, WebM, MOV, MKV.`);
        if (fileInputRef.current) fileInputRef.current.value = '';
        return;
      }
    }

    if (previewUrl) {
      URL.revokeObjectURL(previewUrl);
    }

    setSelectedFile(file);
    setPreviewUrl(URL.createObjectURL(file));
  };

  const handleRemoveFile = () => {
    if (previewUrl) {
      URL.revokeObjectURL(previewUrl);
      setPreviewUrl(null);
    }
    setSelectedFile(null);
    setError('');
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!selectedFile) {
      setError('Please choose a file to upload.');
      return;
    }

    try {
      setLoading(true);
      await onSubmit({
        mediaType,
        file: selectedFile,
        caption: caption.trim() || undefined,
      });

      // Reset form
      if (previewUrl) {
        URL.revokeObjectURL(previewUrl);
        setPreviewUrl(null);
      }
      setSelectedFile(null);
      setCaption('');
      onClose();
    } catch (err: any) {
      setError(err?.response?.data?.message || err?.message || 'Failed to upload media');
    } finally {
      setLoading(false);
    }
  };

  const formatFileSize = (bytes: number) => {
    if (bytes < 1024 * 1024) {
      return `${(bytes / 1024).toFixed(1)} KB`;
    }
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  };

  return (
    <div
      style={{
        position: 'fixed',
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        backgroundColor: 'rgba(0, 0, 0, 0.8)',
        backdropFilter: 'blur(8px)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 1000,
        padding: '1rem',
      }}
    >
      <div
        className="glass-card"
        style={{
          width: '100%',
          maxWidth: '540px',
          backgroundColor: '#0f172a',
          border: '1px solid rgba(255, 255, 255, 0.12)',
          borderRadius: '1.25rem',
          padding: '2rem',
          color: '#f8fafc',
          boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.6)',
          maxHeight: '90vh',
          overflowY: 'auto',
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem' }}>
          <div>
            <h2 style={{ fontSize: '1.35rem', fontWeight: 700, margin: 0, color: '#f8fafc' }}>
              Add Event Memory
            </h2>
            <p style={{ margin: '0.2rem 0 0 0', fontSize: '0.85rem', color: '#94a3b8' }}>
              For {eventTitle}
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            style={{
              background: 'none',
              border: 'none',
              color: '#94a3b8',
              fontSize: '1.5rem',
              cursor: 'pointer',
              lineHeight: 1,
            }}
          >
            &times;
          </button>
        </div>

        {error && (
          <div
            style={{
              backgroundColor: 'rgba(239, 68, 68, 0.15)',
              border: '1px solid rgba(239, 68, 68, 0.35)',
              color: '#f87171',
              padding: '0.75rem 1rem',
              borderRadius: '0.5rem',
              marginBottom: '1.25rem',
              fontSize: '0.875rem',
            }}
          >
            ⚠️ {error}
          </div>
        )}

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
          {/* Media Format */}
          <div>
            <label style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '0.4rem', color: '#cbd5e1' }}>
              Media Format
            </label>
            <div style={{ display: 'flex', gap: '0.75rem' }}>
              <button
                type="button"
                onClick={() => handleMediaTypeChange('PHOTO')}
                className={`btn ${mediaType === 'PHOTO' ? 'btn-primary' : 'btn-secondary'}`}
                style={{ flex: 1, padding: '0.55rem' }}
              >
                📷 Photo / Image
              </button>
              <button
                type="button"
                onClick={() => handleMediaTypeChange('VIDEO')}
                className={`btn ${mediaType === 'VIDEO' ? 'btn-primary' : 'btn-secondary'}`}
                style={{ flex: 1, padding: '0.55rem' }}
              >
                🎥 Video Clip
              </button>
            </div>
          </div>

          {/* Select Media File Picker */}
          <div>
            <label style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '0.4rem', color: '#cbd5e1' }}>
              Select Media
            </label>
            <div
              style={{
                border: '2px dashed rgba(255, 255, 255, 0.18)',
                borderRadius: '0.75rem',
                padding: '1.25rem',
                textAlign: 'center',
                backgroundColor: 'rgba(15, 23, 42, 0.5)',
                cursor: 'pointer',
              }}
              onClick={() => fileInputRef.current?.click()}
            >
              <input
                ref={fileInputRef}
                type="file"
                style={{ display: 'none' }}
                accept={
                  mediaType === 'PHOTO'
                    ? 'image/jpeg,image/png,image/webp,image/jpg'
                    : 'video/mp4,video/webm,video/quicktime,video/x-matroska'
                }
                onChange={handleFileChange}
              />
              <div style={{ fontSize: '1.75rem', marginBottom: '0.35rem' }}>
                {mediaType === 'PHOTO' ? '🖼️' : '🎬'}
              </div>
              <p style={{ margin: '0 0 0.25rem 0', fontWeight: 600, color: '#e2e8f0', fontSize: '0.95rem' }}>
                {selectedFile ? 'Replace selected file' : 'Click to choose a file from your device'}
              </p>
              <p style={{ margin: 0, fontSize: '0.8rem', color: '#94a3b8' }}>
                {mediaType === 'PHOTO'
                  ? 'Supported: JPG, PNG, WEBP (up to 50MB)'
                  : 'Supported: MP4, WebM, MOV, MKV (up to 50MB)'}
              </p>
            </div>
          </div>

          {/* Preview Section */}
          {previewUrl && selectedFile && (
            <div
              style={{
                border: '1px solid rgba(255, 255, 255, 0.12)',
                borderRadius: '0.75rem',
                padding: '0.75rem',
                backgroundColor: 'rgba(2, 6, 23, 0.7)',
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
                <span style={{ fontSize: '0.85rem', fontWeight: 600, color: '#a5b4fc' }}>
                  Preview
                </span>
                <button
                  type="button"
                  onClick={handleRemoveFile}
                  style={{
                    background: 'none',
                    border: 'none',
                    color: '#f87171',
                    fontSize: '0.8rem',
                    cursor: 'pointer',
                    fontWeight: 600,
                  }}
                >
                  Remove
                </button>
              </div>

              <div
                style={{
                  maxHeight: '220px',
                  display: 'flex',
                  justifyContent: 'center',
                  alignItems: 'center',
                  overflow: 'hidden',
                  borderRadius: '0.5rem',
                  backgroundColor: '#000',
                }}
              >
                {mediaType === 'PHOTO' ? (
                  <img
                    src={previewUrl}
                    alt="Upload Preview"
                    style={{
                      maxWidth: '100%',
                      maxHeight: '220px',
                      objectFit: 'contain',
                    }}
                  />
                ) : (
                  <video
                    src={previewUrl}
                    controls
                    style={{
                      maxWidth: '100%',
                      maxHeight: '220px',
                    }}
                  />
                )}
              </div>

              <div
                style={{
                  marginTop: '0.5rem',
                  fontSize: '0.8rem',
                  color: '#94a3b8',
                  display: 'flex',
                  justifyContent: 'space-between',
                }}
              >
                <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', maxWidth: '300px' }}>
                  📄 {selectedFile.name}
                </span>
                <span>{formatFileSize(selectedFile.size)}</span>
              </div>
            </div>
          )}

          {/* Caption / Memory Note */}
          <div>
            <label
              htmlFor="captionInput"
              style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '0.35rem', color: '#cbd5e1' }}
            >
              Caption / Memory Note
            </label>
            <input
              id="captionInput"
              type="text"
              maxLength={255}
              className="form-input"
              value={caption}
              onChange={(e) => setCaption(e.target.value)}
              placeholder="e.g. Chapter 4 debate and coffee break!"
            />
          </div>

          {/* Actions */}
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem', marginTop: '0.5rem' }}>
            <button
              type="button"
              onClick={onClose}
              disabled={loading}
              className="btn btn-secondary"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={loading}
              className="btn btn-primary"
              style={{ display: 'inline-flex', alignItems: 'center', gap: '0.4rem' }}
            >
              {loading ? (
                <>
                  <span className="rc-spinner" style={{ width: '14px', height: '14px' }}></span>
                  <span>Uploading to Gallery...</span>
                </>
              ) : (
                'Add to Gallery'
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
