// This shows the desired behavior. Uncomment the code below and
// implement the missing methods. You will need to update the
// method signatures, including the "self" parameter!
fn main() {
    let mut library = Library::new();

    println!("Our library is empty: {}", library.is_empty());
    
    library.add_book(Book::new("Lord of the Rings", 1954));
    library.add_book(Book::new("Alice's Adventures in Wonderland", 1865));
    
    library.print_books();
    
    match library.oldest_book() {
       Some(book) => println!("My oldest book is {book}"),
       None       => println!("My library is empty!"),
    }
    
    println!("Our library has {} books", library.len());
}

struct Book {
    title: String,
    year: u16,
}

impl Book {
    // This is a constructor, used below.
    fn new(title: &str, year: u16) -> Book {
        Book {
            title: String::from(title),
            year,
        }
    }
}

// This makes it possible to print Book values with {}.
impl std::fmt::Display for Book {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "《{}》 in {}.", self.title, self.year)
    }
}

struct Library {
    books: Vec<Book>,
}

impl Library {
    fn new() -> Library {
        Library { books: vec![] }
    }

    fn len(&self) -> usize {
        self.books.len()
    }

    fn is_empty(&self) -> bool {
        self.len() == 0
    }

    fn add_book(&mut self, book: Book) {
        self.books.push(book);
    }

    fn print_books(&self) {
        for book in &self.books {
            println!("{book}");
        }
    }

    fn oldest_book(&self) -> Option<&Book> {
        self.books.iter().min_by_key(|book| book.year)
    }
}

