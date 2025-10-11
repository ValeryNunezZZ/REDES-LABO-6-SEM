class TiendaApp {
    constructor() {
        this.ws = null;
        this.productos = [];
        this.carrito = new Map(); // Map<productoId, cantidad>
        this.categoriaActual = '';
        this.busquedaActual = '';
        
        this.initializeWebSocket();
        this.bindEvents();
        this.cargarProductos();
    }

    initializeWebSocket() {
        try {
            this.ws = new WebSocket('ws://localhost:9000');

            this.ws.onopen = () => {
                this.updateConnectionStatus(true, 'Conectado al servidor', 'normal');
            };

            this.ws.onmessage = (event) => {
                const data = event.data;

                if (data === "ESTAS_EN_ESPERA") {
                    this.updateConnectionStatus(false, "En espera para conectarte", "espera");
                    return;
                }

                if (data === "AHORA_ACTIVO") {
                    this.updateConnectionStatus(true, "Ahora eres el cliente activo", "normal");
                    return;
                }

                try {
                    this.procesarRespuesta(JSON.parse(data));
                } catch (err) {
                    console.error('Error procesando mensaje del servidor:', err);
                }
            };

            this.ws.onclose = () => {
                this.updateConnectionStatus(false, 'Conexión perdida', 'normal');
                setTimeout(() => this.initializeWebSocket(), 3000);
            };

            this.ws.onerror = (error) => {
                this.updateConnectionStatus(false, 'Error de conexión', 'normal');
            };

        } catch (error) {
            console.error('Error inicializando WebSocket:', error);
            this.updateConnectionStatus(false, 'Error de conexión', 'normal');
        }
    }

    updateConnectionStatus(connected, message, estado='normal') {
        const statusElement = document.getElementById('connectionStatus');
        statusElement.textContent = message;
        statusElement.classList.remove('success', 'error', 'espera');

        switch (estado) {
            case "normal":
                statusElement.classList.add(connected ? 'success' : 'error');
                break;
            case "espera":
                statusElement.classList.add('espera');
                break;
        }

        statusElement.classList.remove('hidden');
    }

    bindEvents() {
        document.querySelectorAll('.nav-tab').forEach(tab => {
            tab.addEventListener('click', () => {
                document.querySelectorAll('.nav-tab').forEach(t => t.classList.remove('active'));
                tab.classList.add('active');
                this.categoriaActual = tab.dataset.category;
                this.cargarProductos();
            });
        });

        document.getElementById('searchBtn').addEventListener('click', () => this.buscarProductos());
        document.getElementById('clearSearch').addEventListener('click', () => {
            document.getElementById('searchInput').value = '';
            this.busquedaActual = '';
            this.cargarProductos();
        });

        document.getElementById('searchInput').addEventListener('keypress', (e) => {
            if (e.key === 'Enter') this.buscarProductos();
        });

        document.getElementById('checkoutBtn').addEventListener('click', () => this.finalizarCompra());
        document.getElementById('clearCartBtn').addEventListener('click', () => this.vaciarCarrito());
    }

    enviarSolicitud(solicitud) {

        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            this.ws.send(JSON.stringify(solicitud));
            console.log("solicitudd => " + solicitud);
        } else {
            this.updateConnectionStatus(false, 'No conectado al servidor', 'normal');
        }
    }

    cargarProductos() {
        this.mostrarLoading(true);
        
        const solicitud = { operacion: 'LISTAR_PRODUCTOS' };
        if (this.categoriaActual) solicitud.categoria = this.categoriaActual;
        
        this.enviarSolicitud(solicitud);
    }

    buscarProductos() {
        const termino = document.getElementById('searchInput').value.trim();
        
        if (!termino) return;
        this.busquedaActual = termino;
        this.mostrarLoading(true);
        this.enviarSolicitud({ operacion: 'BUSCAR_PRODUCTO', nombre: termino });
    }

    procesarRespuesta(respuesta) {
        this.mostrarLoading(false);
        
        if (respuesta.estado === 'exito') {
            if (respuesta.datos) {
                this.productos = respuesta.datos;
                this.mostrarProductos();
            } else if (respuesta.ticket) {
                this.mostrarTicket(respuesta.ticket);
            }
        } else {
            this.mostrarError(respuesta.mensaje || 'Error desconocido');
        }
    }

    mostrarProductos() {
        const grid = document.getElementById('productsGrid');
        grid.innerHTML = '';
    
        let productosFiltrados = this.productos;
    
        // Si hay categoría activa, filtramos
        if (this.categoriaActual) {
            productosFiltrados = productosFiltrados.filter(
                p => p.categoria === this.categoriaActual
            );
        }
    
        if (productosFiltrados.length === 0) {
            grid.innerHTML = '<div class="loading">No se encontraron productos</div>';
            return;
        }
    
        productosFiltrados.forEach(producto => {
            const enCarrito = this.carrito.get(producto.id) || 0;
            const card = this.crearProductoCard(producto, enCarrito);
            grid.appendChild(card);
        });
    
        this.actualizarVistaCarrito();
    }
    

    crearProductoCard(producto, cantidadEnCarrito) {
        const card = document.createElement('div');
        card.className = 'product-card';
        
        const iconos = { electronicos: '💻', ropa: '👕', hogar: '🏠', comida: '🍕' };

        card.innerHTML = `
            <div class="product-image">${iconos[producto.categoria] || '📦'}</div>
            <div class="product-name">${producto.nombre}</div>
            <div class="product-price">$${producto.precio.toFixed(2)}</div>
            <div class="product-stock">Disponible: ${producto.stock} unidades</div>
            <div class="product-actions">
                ${cantidadEnCarrito > 0 ? `
                    <div class="quantity-controls">
                        <button class="quantity-btn" onclick="app.actualizarCantidad(${producto.id}, ${cantidadEnCarrito - 1})">-</button>
                        <span class="quantity-display">${cantidadEnCarrito}</span>
                        <button class="quantity-btn" onclick="app.actualizarCantidad(${producto.id}, ${cantidadEnCarrito + 1})" ${cantidadEnCarrito >= producto.stock ? 'disabled' : ''}>+</button>
                    </div>
                ` : ''}
                <button class="btn-add" onclick="app.agregarAlCarrito(${producto.id})" ${producto.stock === 0 ? 'disabled' : ''}>
                    ${producto.stock === 0 ? 'Sin Stock' : 'Agregar'}
                </button>
            </div>
        `;
        
        return card;
    }

    agregarAlCarrito(productoId) {
        const producto = this.productos.find(p => p.id === productoId);
        if (!producto) return;

        const cantidadActual = this.carrito.get(productoId) || 0;
        if (cantidadActual >= producto.stock) {
            //this.mostrarError('No hay suficiente stock disponible');
            return;
        }

        this.carrito.set(productoId, cantidadActual + 1);
        this.actualizarVistaCarrito();
        this.mostrarProductos();
    }

    actualizarCantidad(productoId, nuevaCantidad) {
        if (nuevaCantidad <= 0) {
            this.carrito.delete(productoId);
        } else {
            const producto = this.productos.find(p => p.id === productoId);
            if (producto && nuevaCantidad <= producto.stock) {
                this.carrito.set(productoId, nuevaCantidad);
            } else {
                this.mostrarError('No hay suficiente stock disponible');
                return;
            }
        }
        
        this.actualizarVistaCarrito();
        this.mostrarProductos();
    }

    actualizarVistaCarrito() {
        const cartSection = document.getElementById('cartSection');
        const cartItems = document.getElementById('cartItems');
        const cartTotal = document.getElementById('cartTotal');

        if (this.carrito.size === 0) {
            cartSection.classList.add('hidden');
            return;
        }

        cartSection.classList.remove('hidden');
        cartItems.innerHTML = '';

        let total = 0;

        this.carrito.forEach((cantidad, productoId) => {
            const producto = this.productos.find(p => p.id === productoId);
            if (producto) {
                const subtotal = producto.precio * cantidad;
                total += subtotal;

                const item = document.createElement('div');
                item.className = 'cart-item';
                item.innerHTML = `
                    <div class="cart-item-info">
                        <div class="cart-item-name">${producto.nombre}</div>
                        <div class="cart-item-details">$${producto.precio.toFixed(2)} x ${cantidad} unidades</div>
                    </div>
                    <div class="cart-item-total">$${subtotal.toFixed(2)}</div>
                    <div class="quantity-controls">
                        <button class="quantity-btn" onclick="app.actualizarCantidad(${productoId}, ${cantidad - 1})">-</button>
                        <button class="quantity-btn" onclick="app.actualizarCantidad(${productoId}, ${cantidad + 1})" ${cantidad >= producto.stock ? 'disabled' : ''}>+</button>
                    </div>
                `;
                cartItems.appendChild(item);
            }
        });

        cartTotal.textContent = total.toFixed(2);
    }

    vaciarCarrito() {
        this.carrito.clear();
        this.actualizarVistaCarrito();
        this.mostrarProductos();
    }

    finalizarCompra() {
        if (this.carrito.size === 0) {
            this.mostrarError('El carrito está vacío');
            return;
        }

        const itemsCompra = [];
        this.carrito.forEach((cantidad, productoId) => {
            itemsCompra.push({ id: productoId, cantidad: cantidad });
        });

        this.enviarSolicitud({ operacion: 'FINALIZAR_COMPRA', carrito: itemsCompra });
    }

    mostrarTicket(ticket) {
        document.getElementById('productsSection').classList.add('hidden');
        document.getElementById('cartSection').classList.add('hidden');

        const ticketSection = document.getElementById('ticketSection');
        ticketSection.classList.remove('hidden');

        let itemsHTML = '';
        ticket.items.forEach(item => {
            itemsHTML += `<div class="ticket-item"><span>${item.nombre} x${item.cantidad}</span><span>$${item.subtotal.toFixed(2)}</span></div>`;
        });

        ticketSection.innerHTML = `
            <div class="ticket-header">
                <h2>🎫 Ticket de Compra</h2>
                <p>Número: #${ticket.numero}</p>
                <p>Fecha: ${ticket.fecha}</p>
            </div>
            ${itemsHTML}
            <div class="ticket-item ticket-total">
                <span>Total:</span>
                <span>$${ticket.total.toFixed(2)}</span>
            </div>
            <div style="text-align: center; margin-top: 2rem;">
                <button class="btn" onclick="app.volverATienda()">Volver a la Tienda</button>
            </div>
        `;

        this.vaciarCarrito();
    }

    volverATienda() {
        document.getElementById('ticketSection').classList.add('hidden');
        document.getElementById('productsSection').classList.remove('hidden');
        this.cargarProductos();
    }

    mostrarLoading(mostrar) {
        const loading = document.getElementById('loading');
        if (mostrar) loading.classList.remove('hidden');
        else loading.classList.add('hidden');
    }

    mostrarError(mensaje) {
        alert(`Error: ${mensaje}`);
    }
}

// Inicializar la aplicación
let app;
document.addEventListener('DOMContentLoaded', () => { app = new TiendaApp(); });