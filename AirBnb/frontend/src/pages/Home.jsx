import Navbar from '../components/Navbar.jsx';
import PropertyCard from '../components/PropertyCard.jsx';
import { categories, listings } from '../data/listings.js';
import { useAuth } from '../context/AuthContext.jsx';
import '../styles/home.css';

export default function Home() {
  const { user } = useAuth();

  return (
    <div className="home">
      <Navbar />

      <nav className="categories">
        <div className="container categories__row">
          {categories.map((c) => (
            <button type="button" key={c.label} className="categories__pill">
              <span className="categories__icon">{c.icon}</span>
              <span>{c.label}</span>
            </button>
          ))}
        </div>
      </nav>

      <main className="container home__main">
        {user && <p className="home__greeting">Welcome back, {user.name.split(' ')[0]} 👋</p>}

        <h1 className="visually-hidden">Places to stay</h1>
        <div className="listings-grid">
          {listings.map((listing) => (
            <PropertyCard key={listing.id} listing={listing} />
          ))}
        </div>
      </main>

      <footer className="home__footer">
        <div className="container">
          <p>This is a demo landing page — listings and search are not backed by real data.</p>
        </div>
      </footer>
    </div>
  );
}
