# Contributing

We use GitHub to manage reviews of pull requests.

* If you have a trivial fix or improvement, go ahead and create a pull
  request, addressing (with `@...`) one or more of the maintainers
  (see [AUTHORS.md](AUTHORS.md)) in the description of the pull request.

* If you plan to do something more involved, first propose your ideas
  in a Github issue. This will avoid unnecessary work and surely give
  you and us a good deal of inspiration.

* Relevant coding style guidelines are the [Google Java Style
  Guide](https://google.github.io/styleguide/javaguide.html) and the
  _Naming_ and _Programming Practices_ sections of Oracle's [Code
  Conventions for the Java Programming
  Language](https://www.oracle.com/java/technologies/javase/codeconventions-contents.html).

* Code must compile clean under `-Xlint:all -Werror`, which the build
  enforces, and `mvn test` must pass before a pull request is merged.
