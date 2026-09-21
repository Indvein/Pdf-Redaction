import React, { useState, useRef, useCallback, useEffect } from 'react';
import axios from 'axios';
import { Upload, Stamp, Download, Loader2, FileText, CheckCircle, ChevronLeft, ChevronRight, Trash2, Square } from 'lucide-react';

const BACKEND_URL = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8274';
const API_BASE = `${BACKEND_URL}/api`;

export default function App() {
  const [step, setStep] = useState(0);
  const [loading, setLoading] = useState(false);
  const [docId, setDocId] = useState(null);
  const [totalPages, setTotalPages] = useState(1);
  const [currentPage, setCurrentPage] = useState(0);
  const [previewUrl, setPreviewUrl] = useState(null);
  const [downloadUrl, setDownloadUrl] = useState(null);

  // Redaction selections: [{page, x, y, width, height}] in natural image px (150 DPI)
  const [selections, setSelections] = useState([]);
  const [wordsText, setWordsText] = useState('');

  // Drag state
  const [isDragging, setIsDragging] = useState(false);
  const [dragAnchor, setDragAnchor] = useState(null);  // {clientX, clientY}
  const [dragCurrent, setDragCurrent] = useState(null); // {clientX, clientY}

  // Stamp
  const [stampPos, setStampPos] = useState(null);  // {page, backendX, backendY}
  const [stampMode, setStampMode] = useState(false); // true = next click places stamp

  const fileInputRef = useRef(null);
  const imgRef = useRef(null);

  useEffect(() => {
    if (docId === null) return;
    axios.get(`${API_BASE}/documents/${docId}/pages/${currentPage}`, { responseType: 'blob' })
      .then(res => setPreviewUrl(URL.createObjectURL(res.data)))
      .catch(err => console.error('Preview failed', err));
  }, [docId, currentPage]);

  // ── File upload ────────────────────────────────────────────────────────────────
  const handleFileUpload = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setLoading(true);
    try {
      const form = new FormData();
      form.append('file', file);
      const res = await axios.post(`${API_BASE}/upload`, form);
      setDocId(res.data.id);
      setTotalPages(res.data.totalPages || 1);
      setCurrentPage(0);
      setStep(1);
    } catch {
      alert('Upload failed. Is the Java API running on port 8274?');
    } finally {
      setLoading(false);
    }
  };

  // ── Coordinate helpers ─────────────────────────────────────────────────────────
  // Convert client coords → natural image pixel coords (what we send to backend)
  const toNatural = (clientX, clientY) => {
    const img = imgRef.current;
    if (!img) return { x: 0, y: 0 };
    const r = img.getBoundingClientRect();
    return {
      x: (clientX - r.left) * (img.naturalWidth  / r.width),
      y: (clientY - r.top)  * (img.naturalHeight / r.height),
    };
  };

  // Convert client coords → position relative to the image element (for overlay CSS)
  const toImgLocal = (clientX, clientY) => {
    const img = imgRef.current;
    if (!img) return { x: 0, y: 0 };
    const r = img.getBoundingClientRect();
    return { x: clientX - r.left, y: clientY - r.top };
  };

  // ── Mouse events on the PDF image ─────────────────────────────────────────────
  const onMouseDown = (e) => {
    e.preventDefault();
    if (stampMode) return; // stamp placed on click, not drag
    setIsDragging(true);
    setDragAnchor({ clientX: e.clientX, clientY: e.clientY });
    setDragCurrent({ clientX: e.clientX, clientY: e.clientY });
  };

  const onMouseMove = (e) => {
    if (!isDragging) return;
    setDragCurrent({ clientX: e.clientX, clientY: e.clientY });
  };

  const onMouseUp = (e) => {
    if (!isDragging || !dragAnchor) return;
    setIsDragging(false);

    const natAnchor  = toNatural(dragAnchor.clientX, dragAnchor.clientY);
    const natCurrent = toNatural(e.clientX, e.clientY);
    const x = Math.min(natAnchor.x, natCurrent.x);
    const y = Math.min(natAnchor.y, natCurrent.y);
    const w = Math.abs(natCurrent.x - natAnchor.x);
    const h = Math.abs(natCurrent.y - natAnchor.y);

    if (w > 6 && h > 6) {
      setSelections(prev => [...prev, { page: currentPage, x, y, width: w, height: h }]);
    }
    setDragAnchor(null);
    setDragCurrent(null);
  };

  const onImageClick = (e) => {
    if (!stampMode) return;
    const nat = toNatural(e.clientX, e.clientY);
    const loc = toImgLocal(e.clientX, e.clientY);
    setStampPos({ page: currentPage, backendX: nat.x, backendY: nat.y, screenX: loc.x, screenY: loc.y });
    setStampMode(false); // exit stamp mode after placing
  };

  // ── Live drag rectangle in screen space ────────────────────────────────────────
  const liveDragStyle = useCallback(() => {
    if (!isDragging || !dragAnchor || !dragCurrent || !imgRef.current) return null;
    const r = imgRef.current.getBoundingClientRect();
    const ax = dragAnchor.clientX  - r.left;
    const ay = dragAnchor.clientY  - r.top;
    const cx = dragCurrent.clientX - r.left;
    const cy = dragCurrent.clientY - r.top;
    return {
      left:   Math.min(ax, cx),
      top:    Math.min(ay, cy),
      width:  Math.abs(cx - ax),
      height: Math.abs(cy - ay),
    };
  }, [isDragging, dragAnchor, dragCurrent]);

  // ── Confirmed selections as screen-space overlays ──────────────────────────────
  const confirmedOverlays = useCallback(() => {
    const img = imgRef.current;
    if (!img || !previewUrl) return [];
    const r = img.getBoundingClientRect();
    const sx = r.width  / img.naturalWidth;
    const sy = r.height / img.naturalHeight;
    return selections
      .filter(s => s.page === currentPage)
      .map((s, i) => ({
        key: i,
        left:   s.x      * sx,
        top:    s.y      * sy,
        width:  s.width  * sx,
        height: s.height * sy,
      }));
  }, [selections, currentPage, previewUrl]);

  // ── Process ────────────────────────────────────────────────────────────────────
  const handleProcess = async () => {
    setLoading(true);
    try {
      const payload = {
        redactionBoxes: selections,
        wordsToRedact: wordsText,
        stamp: stampPos ? { page: stampPos.page, x: stampPos.backendX, y: stampPos.backendY } : null,
      };
      const res = await axios.post(`${API_BASE}/documents/${docId}/process`, payload);
      setDownloadUrl(`${BACKEND_URL}${res.data.url}`);
      setStep(2);
    } catch (err) {
      console.error(err);
      alert('Processing failed. Check the Java console.');
    } finally {
      setLoading(false);
    }
  };

  const reset = () => {
    setStep(0); setDocId(null); setPreviewUrl(null);
    setSelections([]); setWordsText(''); setStampPos(null); setStampMode(false);
  };

  const live = liveDragStyle();
  const overlays = confirmedOverlays();
  const totalSel = selections.length;
  const pageSel  = selections.filter(s => s.page === currentPage).length;

  return (
    <div style={{ minHeight: '100vh', background: '#f8fafc', fontFamily: 'system-ui, sans-serif', color: '#0f172a' }}>

      {/* Header */}
      <header style={{ background: '#fff', borderBottom: '1px solid #e2e8f0', padding: '16px 32px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', position: 'sticky', top: 0, zIndex: 10, boxShadow: '0 1px 4px rgba(0,0,0,0.06)' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <div style={{ background: '#2563eb', padding: 8, borderRadius: 8, color: '#fff', display: 'flex' }}><FileText size={22} /></div>
          <span style={{ fontWeight: 700, fontSize: 18 }}>Redact &amp; Stamp</span>
        </div>
        <div style={{ display: 'flex', gap: 8, fontSize: 13, fontWeight: 500, color: '#64748b' }}>
          {['Upload', 'Edit', 'Download'].map((label, i) => (
            <React.Fragment key={label}>
              {i > 0 && <span>→</span>}
              <span style={{ color: step >= i ? '#2563eb' : undefined }}>{label}</span>
            </React.Fragment>
          ))}
        </div>
      </header>

      <main style={{ maxWidth: 1280, margin: '0 auto', padding: 32 }}>

        {/* ── STEP 0: UPLOAD ── */}
        {step === 0 && (
          <div style={{ display: 'flex', justifyContent: 'center', marginTop: 80 }}>
            <div style={{ background: '#fff', padding: 48, borderRadius: 20, boxShadow: '0 8px 32px rgba(0,0,0,0.08)', border: '1px solid #e2e8f0', textAlign: 'center', maxWidth: 480, width: '100%' }}>
              <div style={{ background: '#eff6ff', width: 72, height: 72, borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 20px', color: '#2563eb' }}>
                <Upload size={30} />
              </div>
              <h2 style={{ fontSize: 26, fontWeight: 700, marginBottom: 12 }}>Upload a Document</h2>
              <p style={{ color: '#64748b', marginBottom: 28 }}>Upload a PDF, then drag over any region to redact it.</p>
              <input type="file" accept=".pdf" style={{ display: 'none' }} ref={fileInputRef} onChange={handleFileUpload} />
              <button
                onClick={() => fileInputRef.current?.click()}
                disabled={loading}
                style={{ background: '#2563eb', color: '#fff', padding: '14px 32px', borderRadius: 12, border: 'none', fontWeight: 600, fontSize: 15, cursor: 'pointer', display: 'inline-flex', alignItems: 'center', gap: 8, opacity: loading ? 0.6 : 1 }}
              >
                {loading ? <Loader2 size={18} className="animate-spin" style={{ animation: 'spin 1s linear infinite' }} /> : <Upload size={18} />}
                {loading ? 'Uploading...' : 'Choose PDF File'}
              </button>
            </div>
          </div>
        )}

        {/* ── STEP 1: EDITOR ── */}
        {step === 1 && (
          <div style={{ display: 'flex', gap: 24, height: 'calc(100vh - 130px)' }}>

            {/* PDF Preview */}
            <div style={{ flex: 1, background: '#fff', borderRadius: 16, boxShadow: '0 4px 16px rgba(0,0,0,0.07)', border: '1px solid #e2e8f0', display: 'flex', flexDirection: 'column', padding: 16, overflow: 'hidden' }}>

              {/* Instruction bar */}
              <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 10, padding: '8px 12px', background: stampMode ? '#eff6ff' : '#fef2f2', borderRadius: 10, border: `1px solid ${stampMode ? '#bfdbfe' : '#fecaca'}`, flexShrink: 0 }}>
                <div style={{ width: 8, height: 8, borderRadius: '50%', background: stampMode ? '#2563eb' : '#ef4444' }} />
                <span style={{ fontSize: 13, fontWeight: 600, color: stampMode ? '#1e40af' : '#b91c1c' }}>
                  {stampMode ? 'Click anywhere on the document to place the stamp' : 'Drag on the document to select redaction zones'}
                </span>
                {stampMode && (
                  <button onClick={() => setStampMode(false)} style={{ marginLeft: 'auto', fontSize: 12, color: '#64748b', background: 'none', border: 'none', cursor: 'pointer' }}>Cancel</button>
                )}
                {pageSel > 0 && !stampMode && (
                  <span style={{ marginLeft: 'auto', fontSize: 12, background: '#fee2e2', color: '#b91c1c', padding: '2px 10px', borderRadius: 99, fontWeight: 600 }}>
                    {pageSel} zone{pageSel !== 1 ? 's' : ''} on this page
                  </span>
                )}
              </div>

              {/* Image area */}
              <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#f1f5f9', borderRadius: 10, overflow: 'hidden' }}>
                <div style={{ position: 'relative', display: 'inline-block', cursor: stampMode ? 'cell' : 'crosshair' }}
                  onMouseDown={onMouseDown}
                  onMouseMove={onMouseMove}
                  onMouseUp={onMouseUp}
                  onMouseLeave={() => { if (isDragging) { setIsDragging(false); setDragAnchor(null); setDragCurrent(null); } }}
                  onClick={onImageClick}
                >
                  {previewUrl && (
                    <img
                      ref={imgRef}
                      src={previewUrl}
                      alt={`Page ${currentPage + 1}`}
                      style={{ display: 'block', maxHeight: 'calc(100vh - 280px)', maxWidth: '100%', objectFit: 'contain', boxShadow: '0 4px 24px rgba(0,0,0,0.15)', userSelect: 'none', pointerEvents: 'none' }}
                      draggable={false}
                    />
                  )}

                  {/* Confirmed zones */}
                  {overlays.map(box => (
                    <div key={box.key} style={{ position: 'absolute', left: box.left, top: box.top, width: box.width, height: box.height, background: 'rgba(239,68,68,0.25)', border: '2px solid #ef4444', pointerEvents: 'none' }} />
                  ))}

                  {/* Live drag */}
                  {live && (
                    <div style={{ position: 'absolute', left: live.left, top: live.top, width: live.width, height: live.height, background: 'rgba(239,68,68,0.15)', border: '2px dashed #ef4444', pointerEvents: 'none' }} />
                  )}

                  {/* Stamp */}
                  {stampPos && stampPos.page === currentPage && imgRef.current && (() => {
                    const r = imgRef.current.getBoundingClientRect();
                    const sx = r.width  / imgRef.current.naturalWidth;
                    const sy = r.height / imgRef.current.naturalHeight;
                    return (
                      <div style={{ position: 'absolute', left: stampPos.backendX * sx - 20, top: stampPos.backendY * sy - 20, width: 40, height: 40, border: '3px solid #2563eb', borderRadius: '50%', background: 'rgba(37,99,235,0.15)', display: 'flex', alignItems: 'center', justifyContent: 'center', pointerEvents: 'none', color: '#2563eb' }}>
                        <Stamp size={18} />
                      </div>
                    );
                  })()}
                </div>
              </div>

              {/* Pagination */}
              {totalPages > 1 && (
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 16, marginTop: 12, flexShrink: 0 }}>
                  <button onClick={() => setCurrentPage(p => Math.max(0, p - 1))} disabled={currentPage === 0}
                    style={{ padding: '6px 14px', borderRadius: 8, border: '1px solid #cbd5e1', background: '#fff', cursor: 'pointer', opacity: currentPage === 0 ? 0.4 : 1, display: 'flex', alignItems: 'center', gap: 4 }}>
                    <ChevronLeft size={15} /> Previous
                  </button>
                  <span style={{ fontSize: 13, color: '#475569', fontWeight: 500 }}>Page {currentPage + 1} of {totalPages}</span>
                  <button onClick={() => setCurrentPage(p => Math.min(totalPages - 1, p + 1))} disabled={currentPage === totalPages - 1}
                    style={{ padding: '6px 14px', borderRadius: 8, border: '1px solid #cbd5e1', background: '#fff', cursor: 'pointer', opacity: currentPage === totalPages - 1 ? 0.4 : 1, display: 'flex', alignItems: 'center', gap: 4 }}>
                    Next <ChevronRight size={15} />
                  </button>
                </div>
              )}
            </div>

            {/* Right Controls Panel */}
            <div style={{ width: 340, display: 'flex', flexDirection: 'column', gap: 16 }}>

              {/* Redaction zones */}
              <div style={{ background: '#fff', borderRadius: 16, boxShadow: '0 4px 16px rgba(0,0,0,0.07)', border: '1px solid #e2e8f0', padding: 24 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
                  <span style={{ background: '#fee2e2', color: '#b91c1c', width: 24, height: 24, borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 12, fontWeight: 700, flexShrink: 0 }}>1</span>
                  <h3 style={{ fontWeight: 700, fontSize: 16, margin: 0 }}>Redaction Zones</h3>
                </div>
                <p style={{ color: '#64748b', fontSize: 13, marginBottom: 14 }}>
                  Drag over <strong>any area</strong> on the document — text, headings, images. Each selection is permanently blacked out.
                </p>

                {totalSel === 0 ? (
                  <div style={{ background: '#f8fafc', border: '1px dashed #cbd5e1', borderRadius: 10, padding: '14px 12px', textAlign: 'center', color: '#94a3b8', fontSize: 13 }}>
                    No zones selected yet
                  </div>
                ) : (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                    {Array.from(new Set(selections.map(s => s.page))).sort((a, b) => a - b).map(pg => {
                      const count = selections.filter(s => s.page === pg).length;
                      return (
                        <div key={pg} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', background: '#fef2f2', border: '1px solid #fecaca', borderRadius: 10, padding: '8px 12px' }}>
                          <span style={{ fontSize: 13, fontWeight: 600, color: '#991b1b' }}>
                            Page {pg + 1} — {count} zone{count !== 1 ? 's' : ''}
                          </span>
                          <button onClick={() => setSelections(p => p.filter(s => s.page !== pg))}
                            style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#f87171', display: 'flex' }}>
                            <Trash2 size={14} />
                          </button>
                        </div>
                      );
                    })}
                    <button onClick={() => setSelections([])} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94a3b8', fontSize: 12, marginTop: 2 }}>
                      Clear all
                    </button>
                  </div>
                )}
              </div>

              {/* Stamp */}
              <div style={{ background: '#fff', borderRadius: 16, boxShadow: '0 4px 16px rgba(0,0,0,0.07)', border: '1px solid #e2e8f0', padding: 24 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
                  <span style={{ background: '#dbeafe', color: '#1d4ed8', width: 24, height: 24, borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 12, fontWeight: 700, flexShrink: 0 }}>2</span>
                  <h3 style={{ fontWeight: 700, fontSize: 16, margin: 0 }}>Approval Stamp</h3>
                </div>
                <p style={{ color: '#64748b', fontSize: 13, marginBottom: 14 }}>Optional: place a stamp on any page.</p>

                {stampPos ? (
                  <div style={{ background: '#f0fdf4', border: '1px solid #bbf7d0', borderRadius: 10, padding: '10px 12px', display: 'flex', alignItems: 'center', gap: 8, color: '#166534' }}>
                    <CheckCircle size={16} />
                    <span style={{ fontSize: 13, fontWeight: 600 }}>Stamp on page {stampPos.page + 1}</span>
                    <button onClick={() => setStampPos(null)} style={{ marginLeft: 'auto', background: 'none', border: 'none', cursor: 'pointer', color: '#86efac' }}><Trash2 size={14} /></button>
                  </div>
                ) : (
                  <button
                    onClick={() => setStampMode(true)}
                    style={{ width: '100%', padding: '10px 0', borderRadius: 10, border: '1px dashed #93c5fd', background: stampMode ? '#dbeafe' : '#f8fafc', color: '#2563eb', fontWeight: 600, fontSize: 13, cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6 }}
                  >
                    <Stamp size={14} /> {stampMode ? 'Now click on the document...' : 'Place Stamp on Document'}
                  </button>
                )}
              </div>

              {/* Text Redaction */}
              <div style={{ background: '#fff', borderRadius: 16, boxShadow: '0 4px 16px rgba(0,0,0,0.07)', border: '1px solid #e2e8f0', padding: 24 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
                  <span style={{ background: '#fef3c7', color: '#b45309', width: 24, height: 24, borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 12, fontWeight: 700, flexShrink: 0 }}>3</span>
                  <h3 style={{ fontWeight: 700, fontSize: 16, margin: 0 }}>Text Search</h3>
                </div>
                <p style={{ color: '#64748b', fontSize: 13, marginBottom: 14 }}>Optionally redact specific words across all pages. Separate with commas.</p>
                <textarea
                  value={wordsText}
                  onChange={(e) => setWordsText(e.target.value)}
                  placeholder="e.g. SECRET, CONFIDENTIAL, John Doe"
                  style={{ width: '100%', minHeight: 80, padding: 12, borderRadius: 10, border: '1px solid #cbd5e1', background: '#f8fafc', fontSize: 13, resize: 'vertical', boxSizing: 'border-box', fontFamily: 'inherit' }}
                />
              </div>

              {/* Apply button */}
              <div style={{ marginTop: 'auto' }}>
                <button
                  onClick={handleProcess}
                  disabled={loading || (totalSel === 0 && !stampPos && !wordsText.trim())}
                  style={{ width: '100%', padding: '16px 0', borderRadius: 14, border: 'none', background: '#0f172a', color: '#fff', fontWeight: 700, fontSize: 16, cursor: totalSel > 0 || stampPos || wordsText.trim() ? 'pointer' : 'not-allowed', opacity: (totalSel > 0 || stampPos || wordsText.trim()) && !loading ? 1 : 0.4, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 10 }}
                >
                  {loading ? <Loader2 size={20} /> : <Square size={20} />}
                  {loading ? 'Processing...' : `Apply${totalSel > 0 || wordsText.trim() ? ` Redaction` : ''}${stampPos ? ' & Stamp' : ''}`}
                </button>
                {totalSel === 0 && !stampPos && !wordsText.trim() && (
                  <p style={{ textAlign: 'center', fontSize: 12, color: '#94a3b8', marginTop: 8 }}>Select a zone, enter text, or place a stamp to continue</p>
                )}
              </div>
            </div>
          </div>
        )}

        {/* ── STEP 2: DONE ── */}
        {step === 2 && (
          <div style={{ display: 'flex', justifyContent: 'center', marginTop: 80 }}>
            <div style={{ background: '#fff', padding: 48, borderRadius: 20, boxShadow: '0 8px 32px rgba(0,0,0,0.08)', border: '1px solid #e2e8f0', textAlign: 'center', maxWidth: 480, width: '100%' }}>
              <div style={{ background: '#f0fdf4', width: 84, height: 84, borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 20px', border: '1px solid #bbf7d0' }}>
                <CheckCircle size={44} color="#16a34a" />
              </div>
              <h2 style={{ fontSize: 26, fontWeight: 700, marginBottom: 12 }}>Complete</h2>
              <p style={{ color: '#64748b', marginBottom: 28 }}>
                Redacted areas are replaced with black pixels. There are no text operators in those regions — nothing can be selected or copied.
              </p>
              <div style={{ display: 'flex', gap: 12, justifyContent: 'center' }}>
                <a href={downloadUrl} download="redacted.pdf"
                  style={{ background: '#2563eb', color: '#fff', padding: '14px 24px', borderRadius: 12, textDecoration: 'none', fontWeight: 600, display: 'flex', alignItems: 'center', gap: 8 }}>
                  <Download size={18} /> Download PDF
                </a>
                <button onClick={reset}
                  style={{ background: '#fff', border: '1px solid #e2e8f0', color: '#374151', padding: '14px 24px', borderRadius: 12, fontWeight: 600, cursor: 'pointer' }}>
                  Process Another
                </button>
              </div>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}
