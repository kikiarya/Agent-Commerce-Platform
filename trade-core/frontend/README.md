# Distributed E-Commerce Platform Frontend

React storefront and operations console for the distributed e-commerce platform.

## Features

### User Interface
- Login with username and password
- Browse available products
- Place orders for products
- View order history and status
- Cancel orders (before delivery)

### Admin Interface
- Manage products (Create, Read, Update, Delete)
- Manage warehouses (Create, Read, Update, Delete)
- View stock levels across all warehouses
- Monitor all orders in the system

## Getting Started

### Prerequisites
- Node.js (v14 or higher)
- npm or yarn

### Installation

1. Install dependencies:
```bash
npm install
```

2. Start the development server:
```bash
npm start
```

The application will open at [http://localhost:3000](http://localhost:3000)

### Default Login Credentials

**Customer Account:**
- Username: `customer`
- Password: `COMP5348`

**Admin Account:**
- Username: `admin`
- Password: `admin123`

## Backend Integration

The frontend connects to the backend API running on `http://localhost:8080/api`

Make sure the following backend services are running:
- Store Service: http://localhost:8080
- Bank Service: http://localhost:8081
- Delivery Service: http://localhost:8082
- Email Service: http://localhost:8083

## Project Structure

```
frontend/
├── public/
│   └── index.html
├── src/
│   ├── context/
│   │   └── AuthContext.js      # Authentication context
│   ├── pages/
│   │   ├── Login.js            # Login page
│   │   ├── UserDashboard.js    # Customer dashboard
│   │   └── AdminDashboard.js   # Admin dashboard
│   ├── services/
│   │   └── api.js              # API service layer
│   ├── App.js                  # Main app component with routing
│   ├── index.js                # Entry point
│   └── index.css               # Global styles
├── package.json
└── README.md
```

## Available Scripts

- `npm start` - Runs the app in development mode
- `npm build` - Builds the app for production
- `npm test` - Runs the test suite
- `npm eject` - Ejects from Create React App (one-way operation)

## Technologies Used

- React 18
- React Router v6
- Axios for API calls
- CSS3 for styling




