import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import Landing from './pages/Landing';
import Auth from './pages/Auth';
import ReaderDashboard from './pages/ReaderDashboard';
import ReaderDiscussion from './pages/ReaderDiscussion';
import ReaderCircles from './pages/ReaderCircles';
import ReaderEvents from './pages/ReaderEvents';
import ReaderChat from './pages/ReaderChat';
import ReaderSettings from './pages/ReaderSettings';
import ReaderBooks from './pages/ReaderBooks';
import ReaderRecommendations from './pages/ReaderRecommendations';
import ReaderCart from './pages/ReaderCart';
import ReaderCheckout from './pages/ReaderCheckout';
import ReaderDiscover from './pages/ReaderDiscover';
import Organizer from './pages/Organizer';
import AdminApp from './pages/admin/AdminApp';
import AdminCircles from './pages/admin/AdminCircles';
import AdminEvents from './pages/admin/AdminEvents';

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Landing />} />
        <Route path="/auth" element={<Auth />} />
        <Route path="/login" element={<Navigate to="/auth" replace />} />
        <Route path="/admin" element={<AdminApp />} />
        <Route path="/circles" element={<AdminCircles />} />
        <Route path="/events" element={<AdminEvents />} />
        <Route path="/reader" element={<ReaderDashboard />} />
        <Route path="/reader-discover" element={<ReaderDiscover />} />
        <Route path="/discover" element={<ReaderDiscover />} />
        <Route path="/reader-discussions/:id" element={<ReaderDiscussion />} />
        <Route path="/reader-circles/*" element={<ReaderCircles />} />
        <Route path="/reader-events/*" element={<ReaderEvents />} />
        <Route path="/reader-chat" element={<ReaderChat />} />
        <Route path="/reader-settings" element={<ReaderSettings />} />
        <Route path="/reader-books" element={<ReaderBooks />} />
        <Route path="/reader-recommendations" element={<ReaderRecommendations />} />
        <Route path="/reader-cart" element={<ReaderCart />} />
        <Route path="/reader-checkout" element={<ReaderCheckout />} />
        <Route path="/organizer" element={<Organizer />} />
        {/* HTML compatibility redirects */}
        <Route path="/index.html" element={<Navigate to="/" replace />} />
        <Route path="/auth.html" element={<Navigate to="/auth" replace />} />
        <Route path="/reader.html" element={<Navigate to="/reader" replace />} />
        <Route path="/discover.html" element={<Navigate to="/reader-discover" replace />} />
        <Route path="/reader-circles.html" element={<Navigate to="/reader-circles" replace />} />
        <Route path="/reader-events.html" element={<Navigate to="/reader-events" replace />} />
        <Route path="/reader-chat.html" element={<Navigate to="/reader-chat" replace />} />
        <Route path="/reader-settings.html" element={<Navigate to="/reader-settings" replace />} />
        <Route path="/reader-books.html" element={<Navigate to="/reader-books" replace />} />
        <Route path="/reader-recommendations.html" element={<Navigate to="/reader-recommendations" replace />} />
        <Route path="/reader-cart.html" element={<Navigate to="/reader-cart" replace />} />
        <Route path="/reader-checkout.html" element={<Navigate to="/reader-checkout" replace />} />
        <Route path="/admin.html" element={<Navigate to="/admin" replace />} />
        <Route path="/circles.html" element={<Navigate to="/circles" replace />} />
        <Route path="/events.html" element={<Navigate to="/events" replace />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
