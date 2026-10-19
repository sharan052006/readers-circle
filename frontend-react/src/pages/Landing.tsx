import { Link } from 'react-router-dom';
import './Landing.css';

export default function Landing() {
  return (
    <div className="landing-wrap">
      <div className="landing-card">
        <h1>Reader&apos;s Circle</h1>
        <p>A community platform for readers, book circles, and literary events.</p>
        <Link className="landing-a" to="/auth">
          Sign In / Create Account
        </Link>
      </div>
    </div>
  );
}
