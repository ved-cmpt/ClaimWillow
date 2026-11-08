import { Route, Routes } from 'react-router-dom';
import { AppShell } from './components/AppShell';
import { StatusPage } from './pages/StatusPage';

export default function App() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route path="*" element={<StatusPage />} />
      </Route>
    </Routes>
  );
}
