import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import Navbar from './components/Navbar';
import Footer from './components/Footer';
import Home from './pages/Home';
import Register from './pages/Register';
import Login from './pages/Login';
import ForgotPassword from './pages/ForgotPassword';
import ResetPassword from './pages/ResetPassword';
import NGOs from './pages/NGOs';
import Events from './pages/Events';
import Needs from './pages/Needs';
import Donate from './pages/Donate';
import Wallet from './pages/Wallet';
import NgoFinance from './pages/NgoFinance';
import Dashboard from './pages/Dashboard';
import AdminDashboard from './pages/AdminDashboard';
import NGODetail from './pages/NGODetail';
import './index.css';

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <div style={{ display: 'flex', flexDirection: 'column', minHeight: '100vh' }}>
          <Navbar />
          <main style={{ flex: 1, paddingTop: '72px' }}>
            <Routes>
              <Route path="/" element={<Home />} />
              <Route path="/register" element={<Register />} />
              <Route path="/login" element={<Login />} />
              <Route path="/forgot-password" element={<ForgotPassword />} />
              <Route path="/reset-password" element={<ResetPassword />} />
              <Route path="/ngos" element={<NGOs />} />
              <Route path="/ngos/:id" element={<NGODetail />} />
              <Route path="/events" element={<Events />} />
              <Route path="/needs" element={<Needs />} />
              <Route path="/donate" element={<Donate />} />
              <Route path="/wallet" element={<Wallet />} />
              <Route path="/ngo/finance" element={<NgoFinance />} />
              <Route path="/dashboard" element={<Dashboard />} />
              <Route path="/admin" element={<AdminDashboard />} />
              <Route path="/profile" element={<Dashboard />} />
            </Routes>
          </main>
          <Footer />
        </div>
      </AuthProvider>
    </BrowserRouter>
  );
}

export default App;
